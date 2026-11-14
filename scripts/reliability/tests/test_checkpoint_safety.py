"""공개 checkpoint 진입점의 소유권·재사용·DB/파일 경계를 검증한다."""
import json,subprocess,sys,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch,Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import checkpoint as cp
import cdc_runtime,configure_cdc

class CheckpointSafetyTest(unittest.TestCase):
    def test_capture_rejects_foreign_topology_before_connector_stop(self):
        # given / when / then
        with patch.object(cp.ops,'require_experiment'),patch.object(cp.runtime,'NAMESPACE','operations'),patch.object(cp,'app_stopped'),patch.object(cp.ops,'verify_topology',side_effect=ValueError('foreign topology')),patch.object(cdc_runtime,'request') as request,patch.object(configure_cdc,'wait_transition'),patch.object(cp.runtime,'compose',side_effect=AssertionError('mutation before ownership')):
            with self.assertRaisesRegex(ValueError,'foreign topology'):cp.capture('guard')
            request.assert_not_called()

    def test_existing_broker_volume_or_delivery_container_is_rejected(self):
        # given / when / then
        for volume,services in [('sentinel-restore_kafka-data\n',''),('','connect\n'),('','kafka\n')]:
            with self.subTest(volume=volume,services=services),patch.object(cp.ops,'require_experiment'),patch.object(cp.runtime,'NAMESPACE','restore'),patch.object(cp.runtime,'PROJECT','sentinel-restore'),patch.object(cp.runtime,'environment',return_value={}),patch.object(cp.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0,volume,''),subprocess.CompletedProcess([],0,services,'')]):
                with self.assertRaises(ValueError):cp.require_fresh_broker()

    def test_fresh_broker_precondition_reads_only_and_accepts_absent_state(self):
        # given / when
        with patch.object(cp.ops,'require_experiment'),patch.object(cp.runtime,'NAMESPACE','restore'),patch.object(cp.runtime,'PROJECT','sentinel-restore'),patch.object(cp.runtime,'environment',return_value={}),patch.object(cp.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0,'',''),subprocess.CompletedProcess([],0,'mysql\nredis\n','')]) as docker:
            result=cp.require_fresh_broker()
        # then
        self.assertEqual([],result['preExistingKafkaVolumes'])
        self.assertEqual([],result['preExistingDeliveryContainers'])
        self.assertEqual([['docker','volume','ls'],['docker','ps','-a']],[call.args[0][:3] for call in docker.call_args_list])

    def test_db_artifact_missing_changed_and_escaping_files_are_rejected(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);file=root/'report.pdf';file.write_bytes(b'%PDF-owned');proof=cp.digest(file)
            valid='\t'.join(('request','report.pdf',str(proof['bytes']),proof['sha256'],'1','report.pdf',str(proof['bytes']),'application/pdf',str(file)))
            with patch.object(cp.ops,'sql',return_value=valid):self.assertEqual(1,len(cp.verify_artifact_files(root)))
            for key in ('missing.pdf','../outside.pdf','/absolute.pdf'):
                with self.subTest(key=key),patch.object(cp.ops,'sql',return_value=valid.replace('report.pdf',key)):
                    with self.assertRaises(ValueError):cp.verify_artifact_files(root)
            file.write_bytes(b'%PDF-wrong')
            with patch.object(cp.ops,'sql',return_value=valid):
                with self.assertRaises(ValueError):cp.verify_artifact_files(root)

    def test_artifact_file_reference_metadata_must_match(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);file=root/'report.pdf';file.write_bytes(b'%PDF-owned');proof=cp.digest(file)
            values=['request','report.pdf',str(proof['bytes']),proof['sha256'],'1','report.pdf',str(proof['bytes']),'application/pdf',str(file)]
            with patch.object(cp.ops,'sql',return_value='\t'.join(values)):self.assertEqual(1,len(cp.verify_artifact_files(root)))
            for field,value in ((4,'NULL'),(5,'other.pdf'),(6,'999'),(7,'text/plain'),(8,str(root/'other.pdf'))):
                changed=list(values);changed[field]=value
                with self.subTest(field=field),patch.object(cp.ops,'sql',return_value='\t'.join(changed)):
                    with self.assertRaises(ValueError):cp.verify_artifact_files(root)

    def restore_fixture(self,root):
        folder=root/'checkpoints/guard';folder.mkdir(parents=True)
        for name in ('schema.sql','data.sql','fixture.json'):(folder/name).write_text('{}')
        with zipfile.ZipFile(folder/'uploads.zip','w'):pass
        manifest=dict(version=1,quiescent=True,running=0,sourceProject='sentinel-operations',files={name:cp.digest(folder/name) for name in cp.FILES},fixture=cp.digest(folder/'fixture.json'),uploads=[],financial={})
        (folder/'manifest.json').write_text(json.dumps(manifest));return folder

    def test_restore_checks_broker_before_preparation_or_import(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);self.restore_fixture(root)
            with patch.object(cp.ops,'require_experiment'),patch.object(cp.runtime,'NAMESPACE','restore'),patch.object(cp.ops,'PRIVATE',root),patch.object(cp,'app_stopped'),patch.object(cp,'require_fresh_broker',side_effect=ValueError('existing broker state'),create=True),patch.object(cp.ops,'prepare',side_effect=AssertionError('preparation before freshness')) as prepare:
                with self.assertRaisesRegex(ValueError,'existing broker state'):cp.restore('guard')
                prepare.assert_not_called()

    def test_restore_rejects_db_file_mismatch_before_configuring_delivery(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);self.restore_fixture(root);state=root/'state';(state/'uploads').mkdir(parents=True)
            with patch.object(cp.ops,'require_experiment'),patch.object(cp.runtime,'NAMESPACE','restore'),patch.object(cp.runtime,'STATE',state),patch.object(cp.ops,'PRIVATE',root),patch.object(cp,'app_stopped'),patch.object(cp,'require_fresh_broker',return_value={},create=True),patch.object(cp.ops,'prepare'),patch.object(cp.ops,'sql',return_value=''),patch.object(cp.ops,'import_sql'),patch.object(cp.ops,'financial',return_value={}),patch.object(cp,'verify_artifact_files',side_effect=ValueError('DB file mismatch'),create=True),patch.object(cp.ops,'configure_delivery') as delivery,patch.object(cp.ops,'write_json'):
                with self.assertRaisesRegex(ValueError,'DB file mismatch'):cp.restore('guard')
                delivery.assert_not_called()

    def test_capture_rejects_db_file_mismatch_before_connector_stop(self):
        # given / when / then
        with patch.object(cp.ops,'require_experiment'),patch.object(cp.runtime,'NAMESPACE','operations'),patch.object(cp.ops,'verify_topology'),patch.object(cp,'app_stopped'),patch.object(cp.ops,'sql',return_value='0'),patch.object(cp,'verify_artifact_files',side_effect=ValueError('DB file mismatch'),create=True),patch.object(cdc_runtime,'request') as request,patch.object(configure_cdc,'wait_transition'),patch.object(cp.runtime,'compose',side_effect=AssertionError('mutation before file audit')):
            with self.assertRaisesRegex(ValueError,'DB file mismatch'):cp.capture('guard')
            request.assert_not_called()
