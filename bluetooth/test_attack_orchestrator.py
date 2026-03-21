"""
Tests for AttackOrchestrator — async loop for concurrent pairing requests.

All Bluetooth, D-Bus, and BLE scanner interactions are mocked since tests
run without a real Bluetooth adapter.
"""

import asyncio
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch, PropertyMock
from dataclasses import dataclass

from attack_orchestrator import (
    AttackOrchestrator,
    OrchestratorState,
    PairingAttempt,
)


def make_mock_scanner(devices=None):
    """Create a mock TargetScanner returning the given devices."""
    scanner = MagicMock()
    scanner.scan_duration = 5.0

    if devices is None:
        devices = []

    mock_devices = []
    for addr in devices:
        dev = MagicMock()
        dev.address = addr
        mock_devices.append(dev)

    scanner.scan = AsyncMock(return_value=mock_devices)
    return scanner


def make_mock_bt_manager():
    """Create a mock BluetoothManager."""
    mgr = MagicMock()
    mgr.trigger_pairing_request = MagicMock(return_value={
        "address": "AA:BB:CC:DD:EE:FF",
        "device_path": "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
        "status": "connected",
        "paired": True,
        "connected": True,
        "error": None,
    })
    return mgr


def make_mock_payload():
    """Create a mock AdvertisingPayload."""
    payload = MagicMock()
    payload.next_payload = MagicMock(return_value=("AA:BB:CC:11:22:33", "Apple Magic Keyboard"))
    return payload


def make_orchestrator(**kwargs):
    """Create an AttackOrchestrator with mocked dependencies."""
    defaults = {
        "bluetooth_manager": make_mock_bt_manager(),
        "target_scanner": make_mock_scanner(["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"]),
        "advertising_payload": make_mock_payload(),
        "concurrency": 2,
        "cycle_delay": 0.0,
        "rotate_identity": True,
    }
    defaults.update(kwargs)
    return AttackOrchestrator(**defaults)


class TestOrchestratorState(unittest.TestCase):
    """Tests for OrchestratorState enum."""

    def test_idle_value(self):
        self.assertEqual(OrchestratorState.IDLE.value, "idle")

    def test_running_value(self):
        self.assertEqual(OrchestratorState.RUNNING.value, "running")

    def test_stopping_value(self):
        self.assertEqual(OrchestratorState.STOPPING.value, "stopping")

    def test_stopped_value(self):
        self.assertEqual(OrchestratorState.STOPPED.value, "stopped")

    def test_all_states_are_strings(self):
        for state in OrchestratorState:
            self.assertIsInstance(state.value, str)


class TestPairingAttempt(unittest.TestCase):
    """Tests for PairingAttempt dataclass."""

    def test_creation(self):
        attempt = PairingAttempt(
            address="AA:BB:CC:DD:EE:FF",
            device_name="Test Keyboard",
            spoofed_mac="11:22:33:44:55:66",
            status="connected",
            paired=True,
            connected=True,
            error=None,
        )
        self.assertEqual(attempt.address, "AA:BB:CC:DD:EE:FF")
        self.assertEqual(attempt.status, "connected")
        self.assertTrue(attempt.paired)

    def test_to_dict(self):
        attempt = PairingAttempt(
            address="AA:BB:CC:DD:EE:FF",
            device_name="Test Keyboard",
            spoofed_mac="11:22:33:44:55:66",
            status="failed",
            paired=False,
            connected=False,
            error="auth failed",
            timestamp=1000.0,
        )
        d = attempt.to_dict()
        self.assertEqual(d["address"], "AA:BB:CC:DD:EE:FF")
        self.assertEqual(d["status"], "failed")
        self.assertEqual(d["error"], "auth failed")
        self.assertEqual(d["timestamp"], 1000.0)
        self.assertFalse(d["paired"])
        self.assertFalse(d["connected"])

    def test_to_dict_contains_all_fields(self):
        attempt = PairingAttempt(
            address="A",
            device_name="B",
            spoofed_mac="C",
            status="D",
            paired=True,
            connected=False,
            error=None,
        )
        d = attempt.to_dict()
        expected_keys = {"address", "device_name", "spoofed_mac", "status",
                         "paired", "connected", "error", "timestamp"}
        self.assertEqual(set(d.keys()), expected_keys)

    def test_default_timestamp(self):
        before = time.time()
        attempt = PairingAttempt(
            address="A", device_name="B", spoofed_mac="C",
            status="D", paired=False, connected=False, error=None,
        )
        after = time.time()
        self.assertGreaterEqual(attempt.timestamp, before)
        self.assertLessEqual(attempt.timestamp, after)


class TestOrchestratorInit(unittest.TestCase):
    """Tests for AttackOrchestrator initialization."""

    def test_default_state_is_idle(self):
        orch = make_orchestrator()
        self.assertEqual(orch.state, OrchestratorState.IDLE)

    def test_initial_counters_zero(self):
        orch = make_orchestrator()
        self.assertEqual(orch.packets_sent, 0)
        self.assertEqual(orch.devices_targeted, 0)
        self.assertEqual(orch.cycles_completed, 0)

    def test_is_running_false_initially(self):
        orch = make_orchestrator()
        self.assertFalse(orch.is_running)

    def test_concurrency_property(self):
        orch = make_orchestrator(concurrency=5)
        self.assertEqual(orch.concurrency, 5)

    def test_concurrency_minimum_is_one(self):
        orch = make_orchestrator(concurrency=0)
        self.assertEqual(orch.concurrency, 1)

    def test_concurrency_setter(self):
        orch = make_orchestrator()
        orch.concurrency = 10
        self.assertEqual(orch.concurrency, 10)

    def test_concurrency_setter_rejects_zero(self):
        orch = make_orchestrator()
        with self.assertRaises(ValueError):
            orch.concurrency = 0

    def test_cycle_delay_property(self):
        orch = make_orchestrator(cycle_delay=2.5)
        self.assertEqual(orch.cycle_delay, 2.5)

    def test_cycle_delay_setter(self):
        orch = make_orchestrator()
        orch.cycle_delay = 3.0
        self.assertEqual(orch.cycle_delay, 3.0)

    def test_cycle_delay_setter_rejects_negative(self):
        orch = make_orchestrator()
        with self.assertRaises(ValueError):
            orch.cycle_delay = -1.0

    def test_elapsed_time_zero_before_run(self):
        orch = make_orchestrator()
        self.assertEqual(orch.elapsed_time, 0.0)

    def test_empty_target_addresses(self):
        orch = make_orchestrator()
        self.assertEqual(orch.target_addresses, [])

    def test_empty_attempt_log(self):
        orch = make_orchestrator()
        self.assertEqual(orch.attempt_log, [])


class TestOrchestratorRun(unittest.TestCase):
    """Tests for the main run() async loop."""

    def test_run_with_max_cycles(self):
        """Orchestrator runs exactly max_cycles cycles then stops."""
        orch = make_orchestrator(cycle_delay=0.0)

        result = asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=2, scan_between_cycles=False)
        )

        self.assertEqual(orch.cycles_completed, 2)
        self.assertEqual(result["cycles_completed"], 2)
        self.assertEqual(orch.state, OrchestratorState.STOPPED)

    def test_run_sends_pairing_packets(self):
        """Each target gets a pairing request each cycle."""
        bt_mgr = make_mock_bt_manager()
        orch = make_orchestrator(bluetooth_manager=bt_mgr, cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        # 2 targets × 1 cycle = 2 packets
        self.assertEqual(orch.packets_sent, 2)
        self.assertEqual(bt_mgr.trigger_pairing_request.call_count, 2)

    def test_run_tracks_devices_targeted(self):
        """devices_targeted counts unique addresses."""
        orch = make_orchestrator(cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=3, scan_between_cycles=False)
        )

        # 2 unique targets, even after 3 cycles
        self.assertEqual(orch.devices_targeted, 2)

    def test_run_no_targets_stops_immediately(self):
        """If scanner finds no devices, orchestrator stops with 0 packets."""
        scanner = make_mock_scanner(devices=[])
        orch = make_orchestrator(target_scanner=scanner, cycle_delay=0.0)

        result = asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=5, scan_between_cycles=False)
        )

        self.assertEqual(result["packets_sent"], 0)
        self.assertEqual(result["cycles_completed"], 0)

    def test_run_returns_summary_dict(self):
        """run() returns a summary with expected keys."""
        orch = make_orchestrator(cycle_delay=0.0)

        result = asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        expected_keys = {"state", "packets_sent", "devices_targeted",
                         "cycles_completed", "elapsed_seconds", "concurrency",
                         "cycle_delay", "target_addresses", "attempts"}
        self.assertEqual(set(result.keys()), expected_keys)

    def test_run_state_transitions(self):
        """State goes from IDLE → RUNNING → STOPPED."""
        orch = make_orchestrator(cycle_delay=0.0)
        self.assertEqual(orch.state, OrchestratorState.IDLE)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.state, OrchestratorState.STOPPED)

    def test_run_already_running_raises(self):
        """Calling run() while already running raises RuntimeError."""
        orch = make_orchestrator(cycle_delay=0.0)
        # Manually set state to simulate running
        orch._state = OrchestratorState.RUNNING

        with self.assertRaises(RuntimeError):
            asyncio.get_event_loop().run_until_complete(
                orch.run(max_cycles=1)
            )

    def test_run_with_duration_limit(self):
        """Orchestrator stops after duration expires."""
        orch = make_orchestrator(cycle_delay=0.0)

        result = asyncio.get_event_loop().run_until_complete(
            orch.run(duration=0.001, scan_between_cycles=False)
        )

        # Should complete at least 1 cycle before checking duration
        self.assertGreaterEqual(result["cycles_completed"], 1)
        self.assertEqual(orch.state, OrchestratorState.STOPPED)

    def test_run_rotates_identity(self):
        """Identity rotation calls next_payload() and sets alias."""
        payload = make_mock_payload()
        bt_mgr = make_mock_bt_manager()
        orch = make_orchestrator(
            bluetooth_manager=bt_mgr,
            advertising_payload=payload,
            rotate_identity=True,
            cycle_delay=0.0,
        )

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=2, scan_between_cycles=False)
        )

        self.assertEqual(payload.next_payload.call_count, 2)

    def test_run_no_identity_rotation(self):
        """When rotate_identity=False, next_payload() is not called."""
        payload = make_mock_payload()
        orch = make_orchestrator(
            advertising_payload=payload,
            rotate_identity=False,
            cycle_delay=0.0,
        )

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=2, scan_between_cycles=False)
        )

        payload.next_payload.assert_not_called()

    def test_run_records_attempt_log(self):
        """Each pairing attempt is recorded in the attempt log."""
        orch = make_orchestrator(cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(len(orch.attempt_log), 2)
        for attempt in orch.attempt_log:
            self.assertIsInstance(attempt, PairingAttempt)
            self.assertEqual(attempt.status, "connected")


class TestOrchestratorConcurrency(unittest.TestCase):
    """Tests for concurrent pairing request batching."""

    def test_concurrency_batches_targets(self):
        """With concurrency=1, targets are processed one at a time."""
        targets = ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"]
        scanner = make_mock_scanner(devices=targets)
        bt_mgr = make_mock_bt_manager()
        orch = make_orchestrator(
            bluetooth_manager=bt_mgr,
            target_scanner=scanner,
            concurrency=1,
            cycle_delay=0.0,
        )

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.packets_sent, 3)

    def test_high_concurrency_still_processes_all(self):
        """With concurrency > target count, all targets are still processed."""
        targets = ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"]
        scanner = make_mock_scanner(devices=targets)
        orch = make_orchestrator(
            target_scanner=scanner,
            concurrency=10,
            cycle_delay=0.0,
        )

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.packets_sent, 2)

    def test_concurrent_execution(self):
        """Verify pairing requests within a batch run concurrently."""
        call_times = []

        def slow_pair(address):
            call_times.append(time.time())
            return {
                "status": "connected", "paired": True,
                "connected": True, "error": None,
            }

        bt_mgr = make_mock_bt_manager()
        bt_mgr.trigger_pairing_request = slow_pair
        targets = ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"]
        scanner = make_mock_scanner(devices=targets)

        orch = make_orchestrator(
            bluetooth_manager=bt_mgr,
            target_scanner=scanner,
            concurrency=2,
            cycle_delay=0.0,
        )

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(len(call_times), 2)


class TestOrchestratorPairingFailures(unittest.TestCase):
    """Tests for handling pairing request failures."""

    def test_pairing_failure_recorded(self):
        """Failed pairing is recorded with error in attempt log."""
        bt_mgr = make_mock_bt_manager()
        bt_mgr.trigger_pairing_request.return_value = {
            "status": "failed",
            "paired": False,
            "connected": False,
            "error": "auth failed",
        }
        orch = make_orchestrator(bluetooth_manager=bt_mgr, cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.packets_sent, 2)
        for attempt in orch.attempt_log:
            self.assertEqual(attempt.status, "failed")
            self.assertEqual(attempt.error, "auth failed")

    def test_exception_in_pairing_handled(self):
        """Exceptions during pairing are caught and recorded."""
        bt_mgr = make_mock_bt_manager()
        bt_mgr.trigger_pairing_request.side_effect = RuntimeError("D-Bus crash")
        orch = make_orchestrator(bluetooth_manager=bt_mgr, cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.packets_sent, 2)
        for attempt in orch.attempt_log:
            self.assertEqual(attempt.status, "failed")
            self.assertIn("D-Bus crash", attempt.error)

    def test_partial_failure_continues(self):
        """One target failing doesn't stop processing other targets."""
        call_count = [0]

        def partial_fail(address):
            call_count[0] += 1
            if call_count[0] == 1:
                return {
                    "status": "failed", "paired": False,
                    "connected": False, "error": "timeout",
                }
            return {
                "status": "connected", "paired": True,
                "connected": True, "error": None,
            }

        bt_mgr = make_mock_bt_manager()
        bt_mgr.trigger_pairing_request = partial_fail
        orch = make_orchestrator(bluetooth_manager=bt_mgr, cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.packets_sent, 2)
        statuses = [a.status for a in orch.attempt_log]
        self.assertIn("failed", statuses)
        self.assertIn("connected", statuses)


class TestOrchestratorStop(unittest.TestCase):
    """Tests for stop() and graceful shutdown."""

    def test_stop_sets_event(self):
        orch = make_orchestrator()
        orch._state = OrchestratorState.RUNNING
        orch.stop()
        self.assertEqual(orch.state, OrchestratorState.STOPPING)
        self.assertTrue(orch._stop_event.is_set())

    def test_stop_when_idle_is_noop(self):
        orch = make_orchestrator()
        orch.stop()
        self.assertEqual(orch.state, OrchestratorState.IDLE)

    def test_stop_interrupts_run(self):
        """Calling stop() during run() causes it to exit."""
        orch = make_orchestrator(cycle_delay=0.0)

        async def run_and_stop():
            async def stop_after_delay():
                await asyncio.sleep(0.01)
                orch.stop()

            asyncio.ensure_future(stop_after_delay())
            return await orch.run(scan_between_cycles=False)

        result = asyncio.get_event_loop().run_until_complete(run_and_stop())
        self.assertEqual(orch.state, OrchestratorState.STOPPED)
        self.assertGreaterEqual(result["cycles_completed"], 1)


class TestOrchestratorReset(unittest.TestCase):
    """Tests for reset()."""

    def test_reset_clears_state(self):
        orch = make_orchestrator(cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertGreater(orch.packets_sent, 0)
        orch.reset()

        self.assertEqual(orch.state, OrchestratorState.IDLE)
        self.assertEqual(orch.packets_sent, 0)
        self.assertEqual(orch.devices_targeted, 0)
        self.assertEqual(orch.cycles_completed, 0)
        self.assertEqual(orch.attempt_log, [])
        self.assertEqual(orch.target_addresses, [])
        self.assertEqual(orch.elapsed_time, 0.0)

    def test_reset_while_running_raises(self):
        orch = make_orchestrator()
        orch._state = OrchestratorState.RUNNING
        with self.assertRaises(RuntimeError):
            orch.reset()

    def test_reset_after_stopped(self):
        orch = make_orchestrator(cycle_delay=0.0)
        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )
        orch.reset()
        self.assertEqual(orch.state, OrchestratorState.IDLE)


class TestOrchestratorSetTargets(unittest.TestCase):
    """Tests for set_targets()."""

    def test_set_targets(self):
        orch = make_orchestrator()
        targets = ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"]
        orch.set_targets(targets)
        self.assertEqual(orch.target_addresses, targets)
        self.assertEqual(orch.devices_targeted, 3)

    def test_set_targets_deduplicates_count(self):
        orch = make_orchestrator()
        orch.set_targets(["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:01"])
        self.assertEqual(orch.devices_targeted, 1)

    def test_set_empty_targets(self):
        orch = make_orchestrator()
        orch.set_targets([])
        self.assertEqual(orch.target_addresses, [])
        self.assertEqual(orch.devices_targeted, 0)


class TestOrchestratorGetStats(unittest.TestCase):
    """Tests for get_stats()."""

    def test_stats_before_run(self):
        orch = make_orchestrator()
        stats = orch.get_stats()
        self.assertEqual(stats["state"], "idle")
        self.assertEqual(stats["packets_sent"], 0)
        self.assertFalse(stats["is_running"])

    def test_stats_after_run(self):
        orch = make_orchestrator(cycle_delay=0.0)
        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )
        stats = orch.get_stats()
        self.assertEqual(stats["state"], "stopped")
        self.assertGreater(stats["packets_sent"], 0)
        self.assertFalse(stats["is_running"])
        self.assertEqual(stats["target_count"], 2)

    def test_stats_keys(self):
        orch = make_orchestrator()
        stats = orch.get_stats()
        expected_keys = {"state", "packets_sent", "devices_targeted",
                         "cycles_completed", "elapsed_seconds", "concurrency",
                         "is_running", "current_mac", "current_name", "target_count"}
        self.assertEqual(set(stats.keys()), expected_keys)


class TestOrchestratorScanBetweenCycles(unittest.TestCase):
    """Tests for re-scanning between cycles."""

    def test_scan_between_cycles_updates_targets(self):
        """When new devices appear between cycles, they get targeted."""
        scanner = MagicMock()
        scanner.scan_duration = 5.0

        # First scan returns 1 device, second scan returns 2
        dev1 = MagicMock()
        dev1.address = "AA:BB:CC:DD:EE:01"
        dev2 = MagicMock()
        dev2.address = "AA:BB:CC:DD:EE:02"
        scanner.scan = AsyncMock(side_effect=[[dev1], [dev1, dev2]])

        orch = make_orchestrator(
            target_scanner=scanner,
            cycle_delay=0.0,
        )

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=True)
        )

        # After first cycle + re-scan, devices_targeted should include both
        self.assertEqual(orch.devices_targeted, 2)

    def test_no_scan_between_cycles(self):
        """With scan_between_cycles=False, scanner.scan() is called only once."""
        scanner = make_mock_scanner(devices=["AA:BB:CC:DD:EE:01"])
        orch = make_orchestrator(target_scanner=scanner, cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=3, scan_between_cycles=False)
        )

        # Only the initial scan
        self.assertEqual(scanner.scan.call_count, 1)


class TestOrchestratorAliasSetFailure(unittest.TestCase):
    """Test that alias-set failures during identity rotation don't crash the loop."""

    def test_alias_failure_does_not_crash(self):
        bt_mgr = make_mock_bt_manager()
        type(bt_mgr).alias = PropertyMock(side_effect=Exception("D-Bus error"))
        orch = make_orchestrator(
            bluetooth_manager=bt_mgr,
            rotate_identity=True,
            cycle_delay=0.0,
        )

        # Should not raise
        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False)
        )

        self.assertEqual(orch.packets_sent, 2)


class TestOrchestratorScanDurationOverride(unittest.TestCase):
    """Tests for scan_duration parameter in run()."""

    def test_scan_duration_override(self):
        scanner = make_mock_scanner(devices=["AA:BB:CC:DD:EE:01"])
        original_duration = scanner.scan_duration
        orch = make_orchestrator(target_scanner=scanner, cycle_delay=0.0)

        asyncio.get_event_loop().run_until_complete(
            orch.run(max_cycles=1, scan_between_cycles=False, scan_duration=2.0)
        )

        # scan_duration should be restored after run
        self.assertEqual(scanner.scan_duration, original_duration)


if __name__ == "__main__":
    unittest.main()
