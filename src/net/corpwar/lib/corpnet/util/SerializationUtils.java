/**************************************************************************
 * CorpNet
 * Copyright (C) 2014 Daniel Ekedahl
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **************************************************************************/
package net.corpwar.lib.corpnet.util;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SerializationUtils {

    private static SerializationUtils serializationUtils = null;
    private static final Logger LOG = Logger.getLogger(SerializationUtils.class.getName());


    private SerializationUtils() {
    }

    public static SerializationUtils getInstance() {
        if (serializationUtils == null) {
            serializationUtils = new SerializationUtils();
        }
        return serializationUtils;
    }

    public <T> byte[] serialize(T obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.reset();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error IOException", e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error IOException", e);
                }
            }
            try {
                baos.close();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Error IOException", e);
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
            LOG.log(Level.SEVERE, "Error IOException", e);
        } catch (ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Error Class not found", e);
            return null;
        } finally {
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error IOException", e);
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error IOException", e);
                }
            }
        }
        return obj;
    }
}
