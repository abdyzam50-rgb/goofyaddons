package com.goofy.goofyaddons.features.companion;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class NodeRuntimeTest {
    @Test void platformPinsChooseNativeBinaryIncludingDarwinNotWindows() {
        assertEquals("win-x64/node.exe",NodeRuntime.artifact("Windows 11","amd64").name());
        assertEquals("node-v24.14.1-darwin-arm64.tar.gz",NodeRuntime.artifact("Darwin","aarch64").name());
        assertEquals("node-v24.14.1-linux-x64/bin/node",NodeRuntime.artifact("Linux","x86_64").member());
        assertThrows(IllegalArgumentException.class,()->NodeRuntime.artifact("Linux","x86"));
    }
    @Test void corruptRuntimeIsRejected() throws Exception {
        byte[] original="trusted".getBytes();String hash=CompanionFiles.digest(original);
        NodeRuntime.verify(original,hash);
        assertThrows(IOException.class,()->NodeRuntime.verify("changed".getBytes(),hash));
    }
    private byte[] tar(char type,String member,byte[] bytes) throws Exception {
        var out=new ByteArrayOutputStream();byte[] header=new byte[512];
        System.arraycopy(member.getBytes(StandardCharsets.US_ASCII),0,header,0,member.length());
        byte[] size=String.format("%011o",bytes.length).getBytes(StandardCharsets.US_ASCII);System.arraycopy(size,0,header,124,size.length);header[156]=(byte)type;
        out.write(header);out.write(bytes);out.write(new byte[(512-bytes.length%512)%512]);out.write(new byte[512]);return out.toByteArray();
    }
    @Test void extractsOnlyExactRegularBinaryAndRejectsLinksMissingAndTruncatedEntries() throws Exception {
        var output=new ByteArrayOutputStream();NodeRuntime.extractTar(new ByteArrayInputStream(tar('0',"root/bin/node","binary".getBytes())),"root/bin/node",output);
        assertEquals("binary",output.toString());
        assertThrows(IOException.class,()->NodeRuntime.extractTar(new ByteArrayInputStream(tar('2',"root/bin/node","target".getBytes())),"root/bin/node",new ByteArrayOutputStream()));
        assertThrows(IOException.class,()->NodeRuntime.extractTar(new ByteArrayInputStream(tar('0',"elsewhere","binary".getBytes())),"root/bin/node",new ByteArrayOutputStream()));
        assertThrows(IOException.class,()->NodeRuntime.extractTar(new ByteArrayInputStream(new byte[200]),"root/bin/node",new ByteArrayOutputStream()));
    }
}
