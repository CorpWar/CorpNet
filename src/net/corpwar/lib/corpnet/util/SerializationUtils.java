package net.corpwar.lib.corpnet.util;

import java.io.*;

/**
 * CorpNet
 * Created by Ghost on 2014-10-24.
 */
public class SerializationUtils {

    private static SerializationUtils serializationUtils = null;


    private SerializationUtils() {
    }

    public static SerializationUtils getInstance() {
        if (serializationUtils == null) {
            serializationUtils = new SerializationUtils();
        }
        return serializationUtils;
    }

    public byte[] serialize(Serializable obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.reset();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    // Ignore error
                }
            }
            try {
                baos.close();
            } catch (IOException e) {
                // Ignore error
                }
        }
        byte[] byteObj = baos.toByteArray();
        baos.reset();
        return byteObj;
    }

    public <T> T deserialize(byte[] bytes) {
        T obj = null;
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;

        try {
            bais = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bais);
            obj = (T) ois.readObject();
        } catch (IOException e) {
            // Ignore any IO error
        } catch (ClassNotFoundException e) {
            // If we can't find class return null
            return null;
        } finally {
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e) {
                    // Ignore error
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    // Ignore error
                }
            }
        }
        return obj;
    }
}
