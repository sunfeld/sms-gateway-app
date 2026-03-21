"""
Unit tests for attack_orchestrator.py documentation and source code docstrings.

Validates that the README correctly documents the AttackOrchestrator module
including its architecture, async orchestration loop, concurrency model,
and that the source code has proper docstrings.
"""

import unittest
import os


def _load_readme():
    """Load README.md content from the project root."""
    readme_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "README.md",
    )
    with open(readme_path, "r") as f:
        return f.read()


def _load_source():
    """Load attack_orchestrator.py source content."""
    source_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "attack_orchestrator.py",
    )
    with open(source_path, "r") as f:
        return f.read()


class TestOrchestratorDocExists(unittest.TestCase):
    """Verify README.md exists and mentions attack_orchestrator."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_readme_file_exists(self):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        self.assertTrue(os.path.exists(readme_path), "README.md not found")

    def test_attack_orchestrator_mentioned_in_readme(self):
        self.assertIn(
            "attack_orchestrator",
            self.readme,
            "attack_orchestrator not documented in README",
        )

    def test_async_orchestration_section_exists(self):
        self.assertIn(
            "Async Orchestration Loop",
            self.readme,
            "Async Orchestration Loop section missing from README",
        )


class TestOrchestratorArchitectureDiagram(unittest.TestCase):
    """Verify attack_orchestrator appears in the architecture diagram."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_architecture_section_exists(self):
        self.assertIn(
            "### Architecture",
            self.readme,
            "Architecture section missing from README",
        )

    def test_orchestrator_in_architecture_diagram(self):
        self.assertIn(
            "attack_orchestrator.py",
            self.readme,
            "attack_orchestrator.py not in architecture diagram",
        )

    def test_target_scanner_in_architecture(self):
        self.assertIn(
            "TargetScanner",
            self.readme,
            "TargetScanner not listed in architecture diagram",
        )

    def test_advertising_payload_in_architecture(self):
        self.assertIn(
            "AdvertisingPayload",
            self.readme,
            "AdvertisingPayload not listed in architecture diagram",
        )

    def test_trigger_pairing_in_architecture(self):
        self.assertIn(
            "trigger_pairing_request()",
            self.readme,
            "trigger_pairing_request() not listed in architecture diagram",
        )

    def test_rapid_advertising_in_architecture(self):
        self.assertIn(
            "start_rapid_advertising()",
            self.readme,
            "start_rapid_advertising() not listed in architecture diagram",
        )


class TestOrchestratorConcurrencyModel(unittest.TestCase):
    """Verify the concurrency model is documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_asyncio_gather_documented(self):
        self.assertIn(
            "asyncio.gather",
            self.readme,
            "asyncio.gather concurrency model not documented",
        )

    def test_concurrent_dispatch_documented(self):
        self.assertIn(
            "send_pairing_request",
            self.readme,
            "send_pairing_request not documented in async orchestration",
        )

    def test_concurrency_model_in_design_decisions(self):
        self.assertIn(
            "Concurrency model",
            self.readme,
            "Concurrency model not listed in design decisions",
        )

    def test_asyncio_event_loop_documented(self):
        self.assertIn(
            "asyncio",
            self.readme,
            "asyncio event loop not documented",
        )


class TestOrchestratorComponentFlow(unittest.TestCase):
    """Verify the component flow documents orchestrator role."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_component_flow_section_exists(self):
        self.assertIn(
            "**Component flow:**",
            self.readme,
            "Component flow section missing from README",
        )

    def test_orchestrator_initializes_event_loop(self):
        self.assertIn(
            "attack_orchestrator.py",
            self.readme,
            "attack_orchestrator.py not mentioned in component flow",
        )

    def test_target_scanner_discovers_devices(self):
        self.assertIn(
            "TargetScanner",
            self.readme,
            "TargetScanner not mentioned in component flow",
        )

    def test_pairing_popups_in_flow(self):
        self.assertIn(
            "pairing pop-ups",
            self.readme,
            "Pairing pop-ups not mentioned in component flow",
        )

    def test_just_works_ssp_in_flow(self):
        self.assertIn(
            "Just Works",
            self.readme,
            "Just Works SSP not mentioned in component flow",
        )

    def test_cycles_through_target_list(self):
        self.assertIn(
            "cycles through the target list",
            self.readme,
            "Cycling through target list not documented",
        )

    def test_clean_shutdown_documented(self):
        self.assertIn(
            "cleanly shuts down",
            self.readme,
            "Clean shutdown not documented in component flow",
        )


class TestOrchestratorDesignDecisions(unittest.TestCase):
    """Verify key design decisions are documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_target_iteration_documented(self):
        self.assertIn(
            "Target iteration",
            self.readme,
            "Target iteration design decision not documented",
        )

    def test_round_robin_cycling_documented(self):
        self.assertIn(
            "Round-robin cycling",
            self.readme,
            "Round-robin cycling strategy not documented",
        )

    def test_rate_control_documented(self):
        self.assertIn(
            "Rate control",
            self.readme,
            "Rate control design decision not documented",
        )

    def test_error_handling_per_target(self):
        self.assertIn(
            "Per-target error isolation",
            self.readme,
            "Per-target error isolation not documented",
        )

    def test_lifecycle_start_stop_documented(self):
        self.assertIn(
            "Lifecycle",
            self.readme,
            "Lifecycle management not documented",
        )

    def test_asyncio_event_flag_documented(self):
        self.assertIn(
            "asyncio.Event",
            self.readme,
            "asyncio.Event flag not documented for lifecycle management",
        )


class TestOrchestratorPlannedFlow(unittest.TestCase):
    """Verify the planned flow steps are documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_planned_flow_section_exists(self):
        self.assertIn(
            "**Planned flow:**",
            self.readme,
            "Planned flow section missing from README",
        )

    def test_target_scanner_populates_list(self):
        self.assertIn(
            "TargetScanner",
            self.readme,
            "TargetScanner not mentioned in planned flow",
        )

    def test_run_loop_documented(self):
        self.assertIn(
            "run_loop()",
            self.readme,
            "run_loop() not documented in planned flow",
        )

    def test_failed_requests_logged(self):
        self.assertIn(
            "Failed requests are logged",
            self.readme,
            "Failed request logging not documented",
        )

    def test_stop_cancels_pending(self):
        self.assertIn(
            "stop()",
            self.readme,
            "stop() function not documented in planned flow",
        )


class TestOrchestratorIntegrationPoint(unittest.TestCase):
    """Verify the FastAPI integration point is documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_fastapi_endpoint_documented(self):
        self.assertIn(
            "POST /api/bluetooth/dos/start",
            self.readme,
            "FastAPI endpoint not documented for orchestrator integration",
        )

    def test_background_task_documented(self):
        self.assertIn(
            "background task",
            self.readme,
            "Background task execution not documented",
        )

    def test_duration_parameter_documented(self):
        self.assertIn(
            "duration",
            self.readme,
            "duration parameter not documented",
        )

    def test_intensity_parameter_documented(self):
        self.assertIn(
            "intensity",
            self.readme,
            "intensity parameter not documented",
        )


class TestOrchestratorSourceModuleDocstring(unittest.TestCase):
    """Verify attack_orchestrator.py has proper module-level docstring."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_module_docstring_present(self):
        self.assertIn(
            "AttackOrchestrator",
            self.source,
            "Module docstring should mention AttackOrchestrator",
        )

    def test_module_mentions_async_loop(self):
        self.assertIn(
            "Asynchronous loop",
            self.source,
            "Module docstring should mention asynchronous loop",
        )

    def test_module_mentions_concurrent_pairing(self):
        self.assertIn(
            "concurrent",
            self.source,
            "Module docstring should mention concurrent pairing requests",
        )

    def test_module_mentions_target_scanner(self):
        self.assertIn(
            "TargetScanner",
            self.source,
            "Module docstring should mention TargetScanner",
        )

    def test_module_mentions_advertising_payload(self):
        self.assertIn(
            "AdvertisingPayload",
            self.source,
            "Module docstring should mention AdvertisingPayload",
        )

    def test_module_mentions_bluetooth_manager(self):
        self.assertIn(
            "BluetoothManager",
            self.source,
            "Module docstring should mention BluetoothManager",
        )


class TestOrchestratorClassDocstring(unittest.TestCase):
    """Verify the AttackOrchestrator class has a proper docstring."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_class_docstring_exists(self):
        self.assertIn(
            "Asynchronous orchestrator that cycles through discovered target devices",
            self.source,
            "AttackOrchestrator class docstring missing",
        )

    def test_class_mentions_ble_discovery(self):
        self.assertIn(
            "discovers nearby Apple/iOS devices via BLE",
            self.source,
            "Class docstring should mention BLE device discovery",
        )

    def test_class_mentions_mac_rotation(self):
        self.assertIn(
            "rotates MAC addresses",
            self.source,
            "Class docstring should mention MAC address rotation",
        )

    def test_class_mentions_dbus_bluez(self):
        self.assertIn(
            "D-Bus/BlueZ",
            self.source,
            "Class docstring should mention D-Bus/BlueZ pairing",
        )

    def test_class_usage_example(self):
        self.assertIn(
            "await orchestrator.run(",
            self.source,
            "Class docstring should include usage example",
        )


class TestOrchestratorInitDocstring(unittest.TestCase):
    """Verify __init__ method has proper parameter documentation."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_init_docstring_present(self):
        self.assertIn(
            "Initialize the AttackOrchestrator",
            self.source,
            "__init__ docstring missing",
        )

    def test_bluetooth_manager_param_documented(self):
        self.assertIn(
            "bluetooth_manager: BluetoothManager instance",
            self.source,
            "bluetooth_manager param not documented",
        )

    def test_target_scanner_param_documented(self):
        self.assertIn(
            "target_scanner: TargetScanner instance",
            self.source,
            "target_scanner param not documented",
        )

    def test_advertising_payload_param_documented(self):
        self.assertIn(
            "advertising_payload: AdvertisingPayload instance",
            self.source,
            "advertising_payload param not documented",
        )

    def test_concurrency_param_documented(self):
        self.assertIn(
            "concurrency: Max number of concurrent pairing requests",
            self.source,
            "concurrency param not documented",
        )

    def test_cycle_delay_param_documented(self):
        self.assertIn(
            "cycle_delay: Delay in seconds between cycles",
            self.source,
            "cycle_delay param not documented",
        )

    def test_rotate_identity_param_documented(self):
        self.assertIn(
            "rotate_identity: Whether to rotate MAC/name between cycles",
            self.source,
            "rotate_identity param not documented",
        )


class TestOrchestratorRunDocstring(unittest.TestCase):
    """Verify the run() method has proper documentation."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_run_docstring_present(self):
        self.assertIn(
            "Run the attack orchestrator loop",
            self.source,
            "run() docstring missing",
        )

    def test_duration_param_documented(self):
        self.assertIn(
            "duration: Maximum run time in seconds",
            self.source,
            "duration parameter not documented in run()",
        )

    def test_max_cycles_param_documented(self):
        self.assertIn(
            "max_cycles: Maximum number of cycles",
            self.source,
            "max_cycles parameter not documented in run()",
        )

    def test_scan_between_cycles_param_documented(self):
        self.assertIn(
            "scan_between_cycles: Whether to re-scan for targets",
            self.source,
            "scan_between_cycles parameter not documented in run()",
        )

    def test_returns_summary_dict(self):
        self.assertIn(
            "Summary dict with stats from the run",
            self.source,
            "run() return value not documented",
        )


class TestOrchestratorMethodDocstrings(unittest.TestCase):
    """Verify other key methods have proper docstrings."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_send_pairing_request_docstring(self):
        self.assertIn(
            "Send a single pairing request to a target address",
            self.source,
            "_send_pairing_request docstring missing",
        )

    def test_send_pairing_uses_executor(self):
        self.assertIn(
            "run_in_executor",
            self.source,
            "_send_pairing_request should use run_in_executor for sync D-Bus calls",
        )

    def test_run_cycle_docstring(self):
        self.assertIn(
            "Run one attack cycle: send concurrent pairing requests",
            self.source,
            "_run_cycle docstring missing",
        )

    def test_stop_docstring(self):
        self.assertIn(
            "Signal the orchestrator to stop after the current cycle completes",
            self.source,
            "stop() docstring missing",
        )

    def test_reset_docstring(self):
        self.assertIn(
            "Reset the orchestrator to its initial state",
            self.source,
            "reset() docstring missing",
        )

    def test_set_targets_docstring(self):
        self.assertIn(
            "Manually set the target address list",
            self.source,
            "set_targets() docstring missing",
        )

    def test_get_stats_docstring(self):
        self.assertIn(
            "Return current orchestrator statistics",
            self.source,
            "get_stats() docstring missing",
        )

    def test_build_summary_docstring(self):
        self.assertIn(
            "Build a summary dict",
            self.source,
            "_build_summary() docstring missing",
        )


class TestOrchestratorDataclassDocstrings(unittest.TestCase):
    """Verify dataclasses and enums have proper docstrings."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_orchestrator_state_docstring(self):
        self.assertIn(
            "Running state of the orchestrator",
            self.source,
            "OrchestratorState docstring missing",
        )

    def test_pairing_attempt_docstring(self):
        self.assertIn(
            "Record of a single pairing attempt",
            self.source,
            "PairingAttempt docstring missing",
        )

    def test_state_idle_exists(self):
        self.assertIn(
            'IDLE = "idle"',
            self.source,
            "OrchestratorState.IDLE not defined",
        )

    def test_state_running_exists(self):
        self.assertIn(
            'RUNNING = "running"',
            self.source,
            "OrchestratorState.RUNNING not defined",
        )

    def test_state_stopping_exists(self):
        self.assertIn(
            'STOPPING = "stopping"',
            self.source,
            "OrchestratorState.STOPPING not defined",
        )

    def test_state_stopped_exists(self):
        self.assertIn(
            'STOPPED = "stopped"',
            self.source,
            "OrchestratorState.STOPPED not defined",
        )


class TestOrchestratorPropertyDocstrings(unittest.TestCase):
    """Verify property docstrings exist."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_state_property_docstring(self):
        self.assertIn(
            "Return the current orchestrator state",
            self.source,
            "state property docstring missing",
        )

    def test_packets_sent_property_docstring(self):
        self.assertIn(
            "Return total pairing request packets sent",
            self.source,
            "packets_sent property docstring missing",
        )

    def test_devices_targeted_property_docstring(self):
        self.assertIn(
            "Return number of unique devices targeted",
            self.source,
            "devices_targeted property docstring missing",
        )

    def test_cycles_completed_property_docstring(self):
        self.assertIn(
            "Return number of completed attack cycles",
            self.source,
            "cycles_completed property docstring missing",
        )

    def test_is_running_property_docstring(self):
        self.assertIn(
            "Return whether the orchestrator is actively running",
            self.source,
            "is_running property docstring missing",
        )

    def test_elapsed_time_property_docstring(self):
        self.assertIn(
            "Return elapsed time since the orchestrator started",
            self.source,
            "elapsed_time property docstring missing",
        )


if __name__ == "__main__":
    unittest.main()
