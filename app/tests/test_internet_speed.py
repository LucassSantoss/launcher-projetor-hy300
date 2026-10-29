"""Valida o teste real de download contra um servidor local controlado."""
import http.server
import pathlib
import subprocess
import tempfile
import threading

ROOT = pathlib.Path(__file__).resolve().parents[1]

class Server(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path == '/redirect':
            self.send_response(302)
            self.send_header('Location', '/ok')
            self.end_headers()
            return
        self.send_response(503 if self.path == '/error' else 200)
        self.send_header('Content-Type', 'text/html' if self.path == '/portal' else 'application/octet-stream')
        self.end_headers()
        if self.path == '/ok':
            self.wfile.write(b'x' * 20_000_000)

TEST = '''package com.mlab.projetor;
import java.io.IOException;
import java.net.URL;
public class InternetSpeedTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        long now = 1000000;
        check(!InternetSpeed.shouldTest(false,false,false,0,now));
        check(!InternetSpeed.shouldTest(false,false,true,0,now));
        check(!InternetSpeed.shouldTest(true,true,true,0,now));
        check(!InternetSpeed.shouldTest(true,true,false,0,now));
        check(InternetSpeed.shouldTest(true,false,true,now,now));
        check(InternetSpeed.shouldTest(true,false,false,0,now));
        check(!InternetSpeed.shouldTest(true,false,false,now,now));
        check(!InternetSpeed.shouldTest(true,false,false,now-299999,now));
        check(InternetSpeed.shouldTest(true,false,false,now-300000,now));
        check(InternetSpeed.shouldTest(true,false,false,now-300001,now));
        check(InternetSpeed.shouldTest(true,false,false,now+1,now));
        check(InternetSpeed.kbps(125000, 1000000000L)==1000);
        check(InternetSpeed.kbps(3125000, 1000000000L)==25000);
        try { InternetSpeed.kbps(0, 1); throw new AssertionError(); } catch(IOException expected) {}
        try { InternetSpeed.kbps(1, 0); throw new AssertionError(); } catch(IOException expected) {}
        check(InternetSpeed.measureKbps(new URL(args[0]+"/ok")) > 0);
        for (String path : new String[]{"/empty", "/error", "/redirect", "/portal"}) {
            try { InternetSpeed.measureKbps(new URL(args[0]+path)); throw new AssertionError(path); }
            catch(IOException expected) {}
        }
        Thread.currentThread().interrupt();
        try { InternetSpeed.measureKbps(new URL(args[0]+"/empty")); throw new AssertionError(); }
        catch(java.io.InterruptedIOException expected) {} finally { Thread.interrupted(); }
        System.out.println("Internet: unidades, download, falhas, portal e cancelamento OK");
    }
}'''
with http.server.ThreadingHTTPServer(('127.0.0.1', 0), Server) as server:
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with tempfile.TemporaryDirectory() as tmp:
            test = pathlib.Path(tmp) / 'InternetSpeedTest.java'
            test.write_text(TEST)
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', tmp,
                            str(ROOT / 'src/com/mlab/projetor/InternetSpeed.java'), str(test)], check=True)
            subprocess.run(['java', '-cp', tmp, 'com.mlab.projetor.InternetSpeedTest',
                            f'http://127.0.0.1:{server.server_port}'], check=True)
    finally:
        server.shutdown()
