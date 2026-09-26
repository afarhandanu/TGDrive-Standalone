"""Transfer regression tests use fake network streams, never live credentials."""
import http.client
import io
import ssl
import sys
import tempfile
import types
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
import media_retry
previous_gallery = sys.modules.get('gallery_dl')
from test_story_picker import picker, Response
if previous_gallery is None:
    sys.modules.pop('gallery_dl', None)
else:
    sys.modules['gallery_dl'] = previous_gallery
import tiktok_embed


class Callback:
    def __init__(self, automatic=True, cancel=False):
        self.automatic = automatic
        self.cancel = cancel
        self.messages = []

    def isAutoRetry(self):
        return self.automatic

    def onProgress(self, percent, message):
        self.messages.append(message)
        if self.cancel and 'Menyambung ulang' in message:
            raise RuntimeError('Dibatalkan')


class Broken(Response):
    def __init__(self):
        super().__init__(b'bad-prefix', 'video/mp4')
        self.reads = 0

    def read(self, size=-1):
        self.reads += 1
        if self.reads > 1:
            raise ssl.SSLError('DECRYPTION_FAILED_OR_BAD_RECORD_MAC')
        return super().read(3)


class RetryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.target = Path(self.temp.name) / 'exact-story-id.mp4'
        sleep = patch.object(media_retry.time, 'sleep')
        sleep.start(); self.addCleanup(sleep.stop)

    def test_ssl_read_failure_reconnects_without_appending_corrupt_prefix(self):
        broken = Broken()
        good = Response(b'correct-video', 'video/mp4')
        with patch.object(picker.urllib.request, 'urlopen', side_effect=[broken, good]) as fetch:
            picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback())
        self.assertEqual(fetch.call_count, 2)
        self.assertEqual(self.target.read_bytes(), b'correct-video')
        self.assertTrue(broken.closed)
        self.assertFalse(list(self.target.parent.glob('*.part')))
        requests = [call.args[0] for call in fetch.call_args_list]
        self.assertIsNot(requests[0], requests[1])
        self.assertEqual(requests[1].get_header('Connection'), 'close')

    def test_manual_policy_does_not_retry(self):
        with patch.object(picker.urllib.request, 'urlopen', side_effect=TimeoutError()) as fetch:
            with self.assertRaises(TimeoutError):
                picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback(False))
        self.assertEqual(fetch.call_count, 1)
        self.assertFalse(self.target.exists())

    def test_exhaustion_is_bounded_and_leaves_no_final_file(self):
        with patch.object(picker.urllib.request, 'urlopen', side_effect=ConnectionResetError()) as fetch:
            with self.assertRaises(ConnectionResetError):
                picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback())
        self.assertEqual(fetch.call_count, 4)
        self.assertFalse(self.target.exists())
        self.assertFalse(list(self.target.parent.glob('*.part')))

    def test_certificate_failure_never_retries_or_disables_verification(self):
        cert = ssl.SSLCertVerificationError('CERTIFICATE_VERIFY_FAILED')
        for error in [cert, urllib.error.URLError(cert)]:
            with patch.object(picker.urllib.request, 'urlopen', side_effect=error) as fetch:
                with self.assertRaises(type(error)):
                    picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback())
            self.assertEqual(fetch.call_count, 1)
            self.assertNotIn('context', fetch.call_args.kwargs)

    def test_wrong_content_does_not_retry(self):
        with patch.object(picker.urllib.request, 'urlopen', return_value=Response(b'login', 'text/html')) as fetch:
            with self.assertRaises(RuntimeError):
                picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback())
        self.assertEqual(fetch.call_count, 1)

    def test_truncated_body_retries_and_preserves_verified_final(self):
        short = Response(b'a', 'video/mp4')
        short.headers.get = lambda name: '9' if name == 'Content-Length' else None
        with patch.object(picker.urllib.request, 'urlopen', side_effect=[short, Response(b'good', 'video/mp4')]) as fetch:
            picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback())
        self.assertEqual(fetch.call_count, 2)
        self.assertEqual(self.target.read_bytes(), b'good')

    def test_cancellation_during_backoff_stops_before_new_connection(self):
        with patch.object(picker.urllib.request, 'urlopen', side_effect=TimeoutError()) as fetch:
            with self.assertRaisesRegex(RuntimeError, 'Dibatalkan'):
                picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback(cancel=True))
        self.assertEqual(fetch.call_count, 1)

    def test_completed_other_media_remains_untouched(self):
        earlier = self.target.parent / 'already-finished.jpg'; earlier.write_bytes(b'original')
        with patch.object(picker.urllib.request, 'urlopen', side_effect=[Broken(), Response(b'next', 'video/mp4')]):
            picker._save_media('https://cdninstagram.com/id.mp4', self.target, Callback())
        self.assertEqual(earlier.read_bytes(), b'original')

    def test_http_and_disk_errors_are_classified(self):
        self.assertTrue(media_retry.transient(urllib.error.HTTPError('url', 503, '', {}, None)))
        self.assertFalse(media_retry.transient(urllib.error.HTTPError('url', 403, '', {}, None)))
        self.assertFalse(media_retry.transient(OSError(28, 'disk full')))
        self.assertFalse(media_retry.transient(RuntimeError('canceled')))
        self.assertTrue(media_retry.transient(http.client.IncompleteRead(b'a', 2)))

    def test_tiktok_media_truncation_retries_only_current_slide(self):
        class Stream(io.BytesIO):
            status = 200
            headers = {'Content-Type': 'image/jpeg', 'Content-Length': '4'}
            def geturl(self): return 'https://p16.tiktokcdn.com/photo.jpg'
        opener = types.SimpleNamespace(open=unittest.mock.Mock(side_effect=[Stream(b'a'), Stream(b'good')]))
        base = self.target.parent / 'slide-02'
        first = self.target.parent / 'slide-01.jpg'; first.write_bytes(b'previous')
        path = tiktok_embed._download_media(opener, 'https://p16.tiktokcdn.com/photo.jpg', base,
            'image', 'https://www.tiktok.com/', Callback())
        self.assertEqual(path.read_bytes(), b'good')
        self.assertEqual(first.read_bytes(), b'previous')
        self.assertEqual(opener.open.call_count, 2)
        self.assertFalse(list(self.target.parent.glob('*.part')))


if __name__ == '__main__':
    unittest.main()
