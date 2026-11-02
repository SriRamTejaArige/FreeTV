package com.sriramteja.glasshome;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * A small adb client: the same wire protocol the laptop's adb uses to reach the TV on port 5555.
 * One command at a time (every public method is synchronized). The first connection makes the TV
 * ask "Allow USB debugging?" for this phone's key; with "Always allow" ticked it never asks again.
 */
final class AdbLink {

    interface Prompt { void showAllowOnTv(); }

    private static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f,
            A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257;
    private static final int VERSION = 0x01000001;
    private static final int OUR_MAX = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** ASN.1 DigestInfo prefix for SHA-1: adbd treats its 20-byte token as a SHA-1 digest. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14};

    private final KeyPair key;
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int maxData = 4096;
    private int nextLocalId = 1;

    AdbLink(File keyDir) throws IOException, GeneralSecurityException {
        key = loadOrMakeKey(keyDir);
    }

    // ------------------------------------------------------------------ connection

    synchronized void connect(String host, int port, Prompt prompt) throws IOException, GeneralSecurityException {
        close();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5000);
        s.setTcpNoDelay(true);
        s.setSoTimeout(10_000);
        socket = s;
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
        send(A_CNXN, VERSION, OUR_MAX, "host::\0".getBytes(StandardCharsets.US_ASCII));
        boolean signed = false, offeredKey = false;
        while (true) {
            Msg m;
            try {
                m = read();
            } catch (SocketTimeoutException e) {
                throw new IOException(offeredKey ? "The TV did not answer. Press Allow on the TV and try again." : "The TV did not answer.");
            }
            if (m.cmd == A_CNXN) {
                maxData = Math.max(4096, Math.min(m.arg1, OUR_MAX));
                s.setSoTimeout(30_000);
                return;
            }
            if (m.cmd != A_AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (!signed) {
                signed = true;
                send(A_AUTH, AUTH_SIGNATURE, 0, sign(m.data));
            } else if (!offeredKey) {
                offeredKey = true;
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBytes());
                if (prompt != null) prompt.showAllowOnTv();
                s.setSoTimeout(90_000);   // time to walk to the TV and press Allow
            } else {
                throw new IOException("The TV refused this phone. Press Allow (tick Always allow) on the TV.");
            }
        }
    }

    synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    synchronized void close() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        socket = null;
        in = null;
        out = null;
    }

    // ------------------------------------------------------------------ shell

    /** Runs a command on the TV and returns everything it printed. */
    synchronized String shell(String command) throws IOException {
        int local = nextLocalId++;
        int remote = open(local, "shell:" + command);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;        // left over from an earlier stream
            if (m.cmd == A_WRTE) {
                result.write(m.data, 0, m.data.length);
                send(A_OKAY, local, remote, null);
            } else if (m.cmd == A_CLSE) {
                send(A_CLSE, local, remote, null);
                break;
            }
        }
        return new String(result.toByteArray(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ push (sync protocol)

    /** Copies bytes to a file on the TV (mode 0644). */
    synchronized void push(byte[] data, String remotePath) throws IOException {
        int local = nextLocalId++;
        int remote = open(local, "sync:");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] spec = (remotePath + ",420").getBytes(StandardCharsets.UTF_8);   // 420 = 0644
        stream.write(syncHeader("SEND", spec.length));
        stream.write(spec);
        for (int off = 0; off < data.length; off += 64 * 1024) {
            int n = Math.min(64 * 1024, data.length - off);
            stream.write(syncHeader("DATA", n));
            stream.write(data, off, n);
        }
        stream.write(syncHeader("DONE", (int) (System.currentTimeMillis() / 1000)));
        byte[] all = stream.toByteArray();
        for (int off = 0; off < all.length; off += maxData) {
            int n = Math.min(maxData, all.length - off);
            byte[] piece = new byte[n];
            System.arraycopy(all, off, piece, 0, n);
            send(A_WRTE, local, remote, piece);
            waitOkay(local);
        }
        // The answer: "OKAY" + 0, or "FAIL" + length + message.
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        while (reply.size() < 8) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == A_WRTE) {
                reply.write(m.data, 0, m.data.length);
                send(A_OKAY, local, remote, null);
            } else if (m.cmd == A_CLSE) {
                break;
            }
        }
        byte[] r = reply.toByteArray();
        String status = r.length >= 4 ? new String(r, 0, 4, StandardCharsets.US_ASCII) : "";
        send(A_WRTE, local, remote, syncHeader("QUIT", 0));
        waitOkayOrClose(local);
        send(A_CLSE, local, remote, null);
        if (!"OKAY".equals(status)) {
            String why = r.length > 8 ? new String(r, 8, r.length - 8, StandardCharsets.UTF_8) : "no answer";
            throw new IOException("Copy to the TV failed: " + why);
        }
    }

    private static byte[] syncHeader(String id, int value) {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes(StandardCharsets.US_ASCII));
        b.putInt(value);
        return b.array();
    }

    // ------------------------------------------------------------------ streams

    private int open(int local, String service) throws IOException {
        if (!isConnected()) throw new IOException("Not connected to the TV.");
        send(A_OPEN, local, 0, (service + "\0").getBytes(StandardCharsets.UTF_8));
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == A_OKAY) return m.arg0;
            if (m.cmd == A_CLSE) throw new IOException("The TV refused: " + service.split(":")[0]);
        }
    }

    private void waitOkay(int local) throws IOException {
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == A_OKAY) return;
            if (m.cmd == A_CLSE) throw new IOException("The TV closed the copy.");
        }
    }

    private void waitOkayOrClose(int local) throws IOException {
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == A_OKAY || m.cmd == A_CLSE) return;
        }
    }

    // ------------------------------------------------------------------ wire format

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        if (out == null) throw new IOException("Not connected to the TV.");
        int len = data == null ? 0 : data.length;
        int sum = 0;
        if (data != null) for (byte b : data) sum += b & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(len).putInt(sum).putInt(~cmd);
        out.write(h.array());
        if (len > 0) out.write(data);
        out.flush();
    }

    private Msg read() throws IOException {
        if (in == null) throw new IOException("Not connected to the TV.");
        byte[] head = new byte[24];
        try {
            in.readFully(head);
        } catch (IOException e) {
            if (!(e instanceof SocketTimeoutException)) close();
            throw e;
        }
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt();
        m.arg0 = h.getInt();
        m.arg1 = h.getInt();
        int len = h.getInt();
        if (len < 0 || len > 16 * 1024 * 1024) {
            close();
            throw new IOException("The TV sent something unexpected.");
        }
        m.data = new byte[len];
        if (len > 0) in.readFully(m.data);
        return m;
    }

    // ------------------------------------------------------------------ key

    private byte[] sign(byte[] token) throws GeneralSecurityException {
        // PKCS#1 v1.5 signature padding built by hand (00 01 FF..FF 00 DigestInfo), then raw RSA with the
        // private key: the same on every phone, whatever its crypto provider does with PKCS1Padding.
        int k = 256;
        byte[] em = new byte[k];
        int t = SHA1_PREFIX.length + token.length;
        em[0] = 0x00;
        em[1] = 0x01;
        for (int i = 2; i < k - t - 1; i++) em[i] = (byte) 0xff;
        em[k - t - 1] = 0x00;
        System.arraycopy(SHA1_PREFIX, 0, em, k - t, SHA1_PREFIX.length);
        System.arraycopy(token, 0, em, k - token.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key.getPrivate());
        return c.doFinal(em);
    }

    /** adb's own public key format (mincrypt RSAPublicKey, base64) followed by a name, NUL-terminated. */
    byte[] publicKeyBytes() {
        RSAPublicKey pub = (RSAPublicKey) key.getPublic();
        BigInteger n = pub.getModulus();
        int words = 64;   // 2048 bits
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putWords(b, n, words);
        putWords(b, rr, words);
        b.putInt(pub.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " glasshome@tv\0";
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static void putWords(ByteBuffer b, BigInteger v, int words) {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        for (int i = 0; i < words; i++) {
            b.putInt(v.shiftRight(32 * i).and(mask).intValue());
        }
    }

    private static KeyPair loadOrMakeKey(File dir) throws IOException, GeneralSecurityException {
        File priv = new File(dir, "adbkey.pk8"), pub = new File(dir, "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            PrivateKey p = kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv)));
            RSAPublicKey q = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(readAll(pub)));
            return new KeyPair(q, p);
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        writeAll(priv, kp.getPrivate().getEncoded());
        writeAll(pub, kp.getPublic().getEncoded());
        return kp;
    }

    static byte[] readAll(File f) throws IOException {
        try (InputStream s = new FileInputStream(f)) {
            return readAll(s);
        }
    }

    static byte[] readAll(InputStream s) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = s.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    static void writeAll(File f, byte[] data) throws IOException {
        try (OutputStream s = new FileOutputStream(f)) {
            s.write(data);
        }
    }
}
