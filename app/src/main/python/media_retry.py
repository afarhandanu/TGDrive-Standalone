"""Bounded retries for one media transfer. TLS verification stays enabled."""
import errno
import http.client
import socket
import ssl
import time
import urllib.error


class IncompleteMediaError(IOError):
    pass


def retry_count(callback):
    # Older callers and desktop tests do not expose the Android policy method.
    method = getattr(callback, 'isAutoRetry', None)
    return 3 if method is None or method() else 0


def transient(error):
    if isinstance(error, urllib.error.HTTPError):
        return error.code in (408, 429, 500, 502, 503, 504)
    if isinstance(error, urllib.error.URLError):
        return transient(error.reason)
    if isinstance(error, (ssl.SSLCertVerificationError, ssl.CertificateError)):
        return False
    if isinstance(error, ssl.SSLError):
        return any(value in str(error).upper() for value in (
            'DECRYPTION_FAILED_OR_BAD_RECORD_MAC', 'BAD_RECORD_MAC',
            'UNEXPECTED_EOF', 'EOF_OCCURRED', 'TIMED_OUT', 'TIMED OUT')) or isinstance(error, ssl.SSLEOFError)
    return isinstance(error, (TimeoutError, ConnectionError, socket.gaierror,
                               http.client.IncompleteRead, http.client.RemoteDisconnected,
                               IncompleteMediaError)) or (
        isinstance(error, OSError) and error.errno in (
            errno.ECONNRESET, errno.ECONNABORTED, errno.ENETUNREACH,
            errno.EHOSTUNREACH, errno.ETIMEDOUT, errno.EPIPE))


def run(operation, callback, name):
    retries = retry_count(callback)
    for attempt in range(retries + 1):
        callback.onProgress(-1, 'Mengunduh media: ' + name)
        try:
            return operation()
        except Exception as error:
            if attempt >= retries or not transient(error):
                raise
            message = f'Menyambung ulang media ({attempt + 1}/{retries}): {name}'
            # Small cancellation checkpoints; never swallow callback exceptions.
            for _ in range(4 * (2 ** attempt)):
                callback.onProgress(-1, message)
                time.sleep(0.25)
