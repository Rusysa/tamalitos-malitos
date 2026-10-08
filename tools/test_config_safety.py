#!/usr/bin/env python3
"""Configuration safety regression checks (stdlib only)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class ConfigurationSafetyTests(unittest.TestCase):
    def test_platform_backup_disabled_and_modern_rules_declared(self):
        manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        app = manifest.find('application')
        self.assertEqual(app.get(ANDROID+'allowBackup'), 'false')
        self.assertEqual(app.get(ANDROID+'dataExtractionRules'), '@xml/data_extraction_rules')
        self.assertEqual(app.get(ANDROID+'fullBackupContent'), 'false')

    def test_modern_cloud_and_device_transfer_exclude_business_storage(self):
        path = ROOT/'app/src/main/res/xml/data_extraction_rules.xml'
        self.assertTrue(path.exists(), 'Android12+ extraction rules are missing')
        rules = ET.parse(path).getroot()
        required = {'root','file','database','sharedpref','external','device_root','device_file','device_database','device_sharedpref'}
        for mode in ['cloud-backup','device-transfer']:
            exclusions = {n.get('domain') for n in rules.find(mode).findall('exclude') if n.get('path')=='.'}
            self.assertTrue(required <= exclusions, f'{mode} leaves business files eligible for extraction')

if __name__ == '__main__': unittest.main(verbosity=2)
