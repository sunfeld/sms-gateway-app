"""
Tests for the FastAPI Bluetooth stress test endpoint.

Verifies POST /api/bluetooth/dos/start accepts duration and intensity
parameters and returns the expected response format.
"""

import asyncio
import sys
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

# Mock dbus before importing bluetooth modules (must match JustWorksAgent inheritance)
if "dbus" not in sys.modules or not hasattr(sys.modules.get("dbus"), "_is_mock"):
    _mock_dbus = MagicMock()
    _mock_dbus._is_mock = True
    _mock_dbus.mainloop = MagicMock()
    _mock_dbus.mainloop.glib = MagicMock()
    _mock_dbus.mainloop.glib.DBusGMainLoop = MagicMock()
    _mock_dbus.SystemBus = MagicMock
    _mock_dbus.Boolean = lambda v: v
    _mock_dbus.String = lambda v: v
    _mock_dbus.UInt32 = lambda v: int(v)
    _mock_dbus.ObjectPath = lambda v: v
    _mock_dbus.Dictionary = lambda d, signature=None: d
    _mock_dbus.Interface = MagicMock()
    _mock_dbus.exceptions = MagicMock()
    _mock_dbus.exceptions.DBusException = Exception

    _mock_service = MagicMock()
    _mock_service.Object = object
    _mock_service.method = lambda iface, **kw: lambda fn: fn
    _mock_dbus.service = _mock_service

    sys.modules["dbus"] = _mock_dbus
    sys.modules["dbus.mainloop"] = _mock_dbus.mainloop
    sys.modules["dbus.mainloop.glib"] = _mock_dbus.mainloop.glib
    sys.modules["dbus.service"] = _mock_service
    sys.modules["dbus.exceptions"] = _mock_dbus.exceptions

sys.modules.setdefault("bleak", MagicMock())
sys.modules.setdefault("bleak.backends.device", MagicMock())
sys.modules.setdefault("bleak.backends.scanner", MagicMock())

from fastapi.testclient import TestClient

from bluetooth.api import app, _orchestrator
import bluetooth.api as api_module


class TestBluetoothDosStartEndpoint(unittest.TestCase):
    """Test POST /api/bluetooth/dos/start endpoint."""

    def setUp(self):
        self.client = TestClient(app)
        # Reset global state between tests
        api_module._orchestrator = None
        api_module._session_id = None
        api_module._session_duration = None
        api_module._session_intensity = None
        api_module._session_start = None
        api_module._run_task = None

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_start_returns_200_with_valid_params(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """Valid request returns 200 with started status."""
        mock_scanner = MockScanner.return_value
        mock_scanner.scan = AsyncMock(return_value=[
            MagicMock(address="AA:BB:CC:DD:EE:01"),
            MagicMock(address="AA:BB:CC:DD:EE:02"),
        ])

        mock_orch = MockOrch.return_value
        mock_orch.state = "idle"
        mock_orch.run = AsyncMock(return_value={})

        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 60, "intensity": 3},
        )

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "started")
        self.assertIn("session_id", data)
        self.assertTrue(data["session_id"].startswith("bt-sess-"))
        self.assertEqual(data["duration"], 60)
        self.assertEqual(data["intensity"], 3)
        self.assertEqual(data["targets_discovered"], 2)

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_start_returns_200_with_intensity_1(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """Intensity level 1 uses concurrency=1 and cycle_delay=0.1."""
        mock_scanner = MockScanner.return_value
        mock_scanner.scan = AsyncMock(return_value=[])
        mock_orch = MockOrch.return_value
        mock_orch.state = "idle"
        mock_orch.run = AsyncMock(return_value={})

        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 10, "intensity": 1},
        )

        self.assertEqual(response.status_code, 200)
        MockOrch.assert_called_once()
        call_kwargs = MockOrch.call_args[1]
        self.assertEqual(call_kwargs["concurrency"], 1)
        self.assertAlmostEqual(call_kwargs["cycle_delay"], 0.100)

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_start_returns_200_with_intensity_5(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """Intensity level 5 uses concurrency=16 and cycle_delay=0.02."""
        mock_scanner = MockScanner.return_value
        mock_scanner.scan = AsyncMock(return_value=[])
        mock_orch = MockOrch.return_value
        mock_orch.state = "idle"
        mock_orch.run = AsyncMock(return_value={})

        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 10, "intensity": 5},
        )

        self.assertEqual(response.status_code, 200)
        MockOrch.assert_called_once()
        call_kwargs = MockOrch.call_args[1]
        self.assertEqual(call_kwargs["concurrency"], 16)
        self.assertAlmostEqual(call_kwargs["cycle_delay"], 0.020)

    def test_start_rejects_missing_duration(self):
        """Missing duration returns 422."""
        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"intensity": 3},
        )
        self.assertEqual(response.status_code, 422)

    def test_start_rejects_missing_intensity(self):
        """Missing intensity returns 422."""
        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 60},
        )
        self.assertEqual(response.status_code, 422)

    def test_start_rejects_intensity_out_of_range_high(self):
        """Intensity > 5 returns 422."""
        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 60, "intensity": 6},
        )
        self.assertEqual(response.status_code, 422)

    def test_start_rejects_intensity_out_of_range_low(self):
        """Intensity < 1 returns 422."""
        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 60, "intensity": 0},
        )
        self.assertEqual(response.status_code, 422)

    def test_start_rejects_negative_duration(self):
        """Negative duration returns 422."""
        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": -1, "intensity": 3},
        )
        self.assertEqual(response.status_code, 422)

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_returns_409_when_already_running(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """Returns 409 when an orchestrator session is already running."""
        from bluetooth.attack_orchestrator import OrchestratorState

        mock_orch = MagicMock()
        mock_orch.state = OrchestratorState.RUNNING

        api_module._orchestrator = mock_orch
        api_module._session_id = "bt-sess-1234567890"
        api_module._session_duration = 60
        api_module._session_start = time.time()

        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 30, "intensity": 2},
        )

        self.assertEqual(response.status_code, 409)
        data = response.json()["detail"]
        self.assertEqual(data["status"], "already_running")
        self.assertEqual(data["session_id"], "bt-sess-1234567890")
        self.assertIn("remaining_seconds", data)

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_session_id_format(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """Session ID follows bt-sess-{timestamp} format."""
        mock_scanner = MockScanner.return_value
        mock_scanner.scan = AsyncMock(return_value=[])
        mock_orch = MockOrch.return_value
        mock_orch.state = "idle"
        mock_orch.run = AsyncMock(return_value={})

        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 10, "intensity": 1},
        )

        data = response.json()
        session_id = data["session_id"]
        self.assertTrue(session_id.startswith("bt-sess-"))
        timestamp_part = session_id.replace("bt-sess-", "")
        self.assertTrue(timestamp_part.isdigit())

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_scan_failure_returns_zero_targets(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """If target scan fails, targets_discovered is 0."""
        mock_scanner = MockScanner.return_value
        mock_scanner.scan = AsyncMock(side_effect=Exception("No adapter"))
        mock_orch = MockOrch.return_value
        mock_orch.state = "idle"
        mock_orch.run = AsyncMock(return_value={})

        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 10, "intensity": 1},
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["targets_discovered"], 0)

    def test_endpoint_rejects_get_method(self):
        """GET method on the endpoint returns 405."""
        response = self.client.get("/api/bluetooth/dos/start")
        self.assertEqual(response.status_code, 405)

    def test_empty_body_returns_422(self):
        """Empty request body returns 422."""
        response = self.client.post(
            "/api/bluetooth/dos/start",
            json={},
        )
        self.assertEqual(response.status_code, 422)


    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_start_sets_targets_on_orchestrator(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """Discovered targets are passed to orchestrator via set_targets."""
        mock_scanner = MockScanner.return_value
        devices = [
            MagicMock(address="AA:BB:CC:DD:EE:01"),
            MagicMock(address="AA:BB:CC:DD:EE:02"),
        ]
        mock_scanner.scan = AsyncMock(return_value=devices)
        mock_orch = MockOrch.return_value
        mock_orch.state = "idle"
        mock_orch.run = AsyncMock(return_value={})

        self.client.post(
            "/api/bluetooth/dos/start",
            json={"duration": 10, "intensity": 3},
        )

        mock_orch.set_targets.assert_called_once_with(
            ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"]
        )

    @patch("bluetooth.api.TargetScanner")
    @patch("bluetooth.api.BluetoothManager")
    @patch("bluetooth.api.AdvertisingPayload")
    @patch("bluetooth.api.AttackOrchestrator")
    def test_all_intensity_levels_map_correctly(
        self, MockOrch, MockPayload, MockBT, MockScanner
    ):
        """All 5 intensity levels map to correct concurrency and cycle_delay."""
        expected = {
            1: (0.100, 1),
            2: (0.050, 2),
            3: (0.030, 4),
            4: (0.020, 8),
            5: (0.020, 16),
        }
        for level, (exp_delay, exp_conc) in expected.items():
            # Reset state
            api_module._orchestrator = None
            api_module._session_id = None
            api_module._session_duration = None
            api_module._session_intensity = None
            api_module._session_start = None
            api_module._run_task = None
            MockOrch.reset_mock()

            mock_scanner = MockScanner.return_value
            mock_scanner.scan = AsyncMock(return_value=[])
            mock_orch = MockOrch.return_value
            mock_orch.state = "idle"
            mock_orch.run = AsyncMock(return_value={})

            response = self.client.post(
                "/api/bluetooth/dos/start",
                json={"duration": 10, "intensity": level},
            )
            self.assertEqual(response.status_code, 200, f"Intensity {level} failed")
            call_kwargs = MockOrch.call_args[1]
            self.assertEqual(call_kwargs["concurrency"], exp_conc,
                             f"Intensity {level}: wrong concurrency")
            self.assertAlmostEqual(call_kwargs["cycle_delay"], exp_delay,
                                   msg=f"Intensity {level}: wrong cycle_delay")


class TestBluetoothDosStatusEndpoint(unittest.TestCase):
    """Test GET /api/bluetooth/dos/status/{session_id} endpoint."""

    def setUp(self):
        self.client = TestClient(app)
        api_module._orchestrator = None
        api_module._session_id = None
        api_module._session_duration = None
        api_module._session_intensity = None
        api_module._session_start = None
        api_module._run_task = None

    def test_status_returns_404_for_unknown_session(self):
        """Unknown session_id returns 404."""
        response = self.client.get("/api/bluetooth/dos/status/bt-sess-unknown")
        self.assertEqual(response.status_code, 404)

    def test_status_returns_stats_for_active_session(self):
        """Active session returns stats from orchestrator."""
        mock_orch = MagicMock()
        mock_orch.get_stats.return_value = {
            "state": "running",
            "packets_sent": 42,
            "devices_targeted": 3,
            "cycles_completed": 5,
            "elapsed_seconds": 10.0,
            "concurrency": 4,
            "is_running": True,
            "current_mac": "AA:BB:CC:DD:EE:FF",
            "current_name": "Test",
            "target_count": 3,
        }

        api_module._orchestrator = mock_orch
        api_module._session_id = "bt-sess-1234567890"
        api_module._session_duration = 60
        api_module._session_intensity = 3
        api_module._session_start = time.time()

        response = self.client.get("/api/bluetooth/dos/status/bt-sess-1234567890")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["session_id"], "bt-sess-1234567890")
        self.assertEqual(data["status"], "running")
        self.assertEqual(data["packets_sent"], 42)
        self.assertEqual(data["targets_active"], 3)
        self.assertEqual(data["intensity"], 3)
        self.assertIn("remaining_seconds", data)


class TestBluetoothDosStopEndpoint(unittest.TestCase):
    """Test POST /api/bluetooth/dos/stop/{session_id} endpoint."""

    def setUp(self):
        self.client = TestClient(app)
        api_module._orchestrator = None
        api_module._session_id = None
        api_module._session_duration = None
        api_module._session_intensity = None
        api_module._session_start = None
        api_module._run_task = None

    def test_stop_returns_404_for_unknown_session(self):
        """Unknown session_id returns 404."""
        response = self.client.post("/api/bluetooth/dos/stop/bt-sess-unknown")
        self.assertEqual(response.status_code, 404)

    def test_stop_calls_orchestrator_stop(self):
        """Stopping an active session calls orchestrator.stop()."""
        mock_orch = MagicMock()
        mock_orch.is_running = True

        api_module._orchestrator = mock_orch
        api_module._session_id = "bt-sess-1234567890"

        response = self.client.post("/api/bluetooth/dos/stop/bt-sess-1234567890")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "stopped")
        self.assertEqual(data["session_id"], "bt-sess-1234567890")
        mock_orch.stop.assert_called_once()

    def test_stop_does_not_call_stop_if_not_running(self):
        """If orchestrator is not running, stop() is not called."""
        mock_orch = MagicMock()
        mock_orch.is_running = False

        api_module._orchestrator = mock_orch
        api_module._session_id = "bt-sess-1234567890"

        response = self.client.post("/api/bluetooth/dos/stop/bt-sess-1234567890")
        self.assertEqual(response.status_code, 200)
        mock_orch.stop.assert_not_called()


if __name__ == "__main__":
    unittest.main()
