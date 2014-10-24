package net.corpwar.lib.corpnet.util;


import org.junit.Assert;
import org.junit.Test;

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



}
