"""What tools/snapshot.py undoes, decided from two TV states without a TV.

plan() is the whole restore decision: everything else in snapshot.py reads the TV or sends the
commands it returns. The properties worth pinning are the ones a mistake would make expensive: an app
the owner installed is never uninstalled, Home is set after the launcher is gone, and a package that
was off before the snapshot goes back off.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import snapshot  # noqa: E402

STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
CUSTOM_HOME = "com.gathin.tvhome/.MainActivity"
LEANBACK_IME = "com.google.android.leanback.ime/.LeanbackImeService"


def tv_state(**changes):
    state = {
        "packages": {
            "com.google.android.tvlauncher": "enabled",
            "tv.samba.ssm": "enabled",
            "com.sony.dtv.b2b.softap": "disabled",
        },
        "not_for_user": [],
        "home": STOCK_HOME,
        "settings": {
            "system": {"screen_brightness": "100"},
            "secure": {"default_input_method": LEANBACK_IME},
            "global": {"window_animation_scale": "1.0"},
        },
    }
    state.update(copy.deepcopy(changes))
    return state


class PlanTest(unittest.TestCase):

    def test_an_unchanged_tv_needs_nothing(self):
        commands, notes = snapshot.plan(tv_state(), tv_state())

        self.assertEqual(commands, [])
        self.assertEqual(notes, [])

    def test_a_disabled_package_is_enabled_and_home_set_again(self):
        current = tv_state()
        current["packages"]["tv.samba.ssm"] = "disabled"

        commands, _ = snapshot.plan(tv_state(), current)

        self.assertEqual(commands, ["pm enable tv.samba.ssm",
                                    f"cmd package set-home-activity --user 0 {STOCK_HOME}"])

    def test_a_package_off_before_the_snapshot_goes_back_off(self):
        current = tv_state()
        current["packages"]["com.sony.dtv.b2b.softap"] = "enabled"

        commands, _ = snapshot.plan(tv_state(), current)

        self.assertIn("pm disable-user --user 0 com.sony.dtv.b2b.softap", commands)

    def test_the_custom_launcher_is_uninstalled_before_home_goes_back(self):
        current = tv_state(home=CUSTOM_HOME)
        current["packages"]["com.google.android.tvlauncher"] = "disabled"
        current["packages"]["com.gathin.tvhome"] = "enabled"

        commands, _ = snapshot.plan(tv_state(), current)

        self.assertEqual(commands, ["pm enable com.google.android.tvlauncher",
                                    "pm uninstall com.gathin.tvhome",
                                    f"cmd package set-home-activity --user 0 {STOCK_HOME}"])

    def test_an_app_the_owner_installed_later_is_left_alone(self):
        current = tv_state()
        current["packages"]["com.example.newapp"] = "enabled"

        commands, notes = snapshot.plan(tv_state(), current)

        self.assertEqual(commands, [])
        self.assertEqual(notes, ["com.example.newapp was installed after the snapshot; left alone"])

    def test_a_package_removed_for_the_user_is_installed_again(self):
        current = tv_state(not_for_user=["tv.samba.ssm"])
        del current["packages"]["tv.samba.ssm"]

        commands, _ = snapshot.plan(tv_state(), current)

        self.assertIn("cmd package install-existing tv.samba.ssm", commands)

    def test_touched_settings_are_put_back_and_added_ones_deleted(self):
        current = tv_state()
        current["settings"]["secure"]["default_input_method"] = "com.example.keyboard/.Ime"
        current["settings"]["global"]["window_animation_scale"] = "0.5"
        current["settings"]["global"]["private_dns_mode"] = "hostname"

        commands, _ = snapshot.plan(tv_state(), current)

        self.assertCountEqual(commands, [
            "settings put global window_animation_scale 1.0",
            "settings delete global private_dns_mode",
            f"settings put secure default_input_method {LEANBACK_IME}",
        ])

    def test_a_setting_value_with_shell_syntax_is_quoted(self):
        before = tv_state()
        before["settings"]["secure"]["enabled_accessibility_services"] = "a/.B:c/.D; reboot"

        commands, _ = snapshot.plan(before, tv_state())

        self.assertEqual(commands,
                         ["settings put secure enabled_accessibility_services 'a/.B:c/.D; reboot'"])

    def test_other_settings_are_reported_not_reverted(self):
        current = tv_state()
        current["settings"]["system"]["screen_brightness"] = "40"

        commands, notes = snapshot.plan(tv_state(), current)

        self.assertEqual(commands, [])
        self.assertEqual(notes, ["system settings changed since, not reverted: screen_brightness"])

    def test_a_name_that_is_not_a_package_never_reaches_a_command(self):
        before = tv_state()
        before["packages"]["x; reboot"] = "enabled"

        commands, _ = snapshot.plan(before, tv_state())

        self.assertEqual(commands, [])


if __name__ == "__main__":
    unittest.main()
