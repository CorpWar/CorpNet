package net.corpwar.lib.corpnet.util;


import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * CorpNet
 * Created by Ghost on 2014-10-24.
 */
public class SerializationUtilsTest {


    @Test
    public void serializeTest() {
        TestSerialization testSerialization = new TestSerialization();
        byte[] testByte = SerializationUtils.getInstance().serialize(testSerialization);
        Assert.assertTrue(testByte.length > 0);
    }

    @Test
    public void deserializeTest() {
        TestSerialization testSerialization = new TestSerialization();
        byte[] testByte = SerializationUtils.getInstance().serialize(testSerialization);
        Assert.assertTrue(testByte.length > 0);

        TestSerialization returnObj = SerializationUtils.getInstance().deserialize(testByte);
        Assert.assertEquals(testSerialization, returnObj);
    }

    @Test
    public void byteBufferTest() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        byteBuffer.clear();
        byteBuffer.putFloat(124).putFloat(124).putInt(1).putInt(3);
        byte[] test = new byte[byteBuffer.position()];
        byteBuffer.flip();
        byteBuffer.get(test);

        System.out.println(test.length);
    }



}
