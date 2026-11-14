"""복원 전에 손상/경로/소유권을 거부한다."""
import hashlib,json,sys,tempfile,unittest,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import checkpoint

class CheckpointTest(unittest.TestCase):
    def fixture(self,root):
        for name in ('schema.sql','data.sql','uploads.zip'):
            (root/name).write_bytes(b'owned')
        manifest={'version':1,'quiescent':True,'running':0,'sourceProject':'sentinel-operations',
            'files':{name:{'bytes':5,'sha256':hashlib.sha256(b'owned').hexdigest()} for name in ('schema.sql','data.sql','uploads.zip')}}
        (root/'manifest.json').write_text(json.dumps(manifest));return manifest

    def test_tampered_or_missing_file_is_rejected_before_database_work(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);self.fixture(root)
            checkpoint.validate(root)
            (root/'data.sql').write_bytes(b'wrong')
            with self.assertRaises(ValueError):checkpoint.validate(root)
            (root/'data.sql').unlink()
            with self.assertRaises(ValueError):checkpoint.validate(root)

    def test_live_or_original_checkpoint_is_rejected(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);manifest=self.fixture(root)
            for key,value in [('quiescent',False),('running',1),('sourceProject','sentinel-portfolio')]:
                changed=dict(manifest);changed[key]=value;(root/'manifest.json').write_text(json.dumps(changed))
                with self.assertRaises(ValueError):checkpoint.validate(root)

    def test_zip_traversal_symlink_and_absolute_members_are_rejected(self):
        # given / when / then
        for name in ('../escape','/absolute','a/../../escape','C:/escape'):
            with self.subTest(name=name),tempfile.TemporaryDirectory() as directory:
                path=Path(directory)/'files.zip'
                with zipfile.ZipFile(path,'w') as archive:archive.writestr(name,b'data')
                with self.assertRaises(ValueError):checkpoint.validate_archive(path)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'files.zip'
            with zipfile.ZipFile(path,'w') as archive:
                member=zipfile.ZipInfo('link');member.external_attr=(0o120777<<16);archive.writestr(member,b'elsewhere')
            with self.assertRaises(ValueError):checkpoint.validate_archive(path)
