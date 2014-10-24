package net.corpwar.lib.corpnet.util;

import java.io.Serializable;

/**
 * CorpNet
 * Created by Ghost on 2014-10-24.
 */
public class TestSerialization implements Serializable {

    private int integer;
    private long longer;
    private String string;

    public TestSerialization() {
        integer = 100;
        longer = 10000l;
        string = "Some string to do stuff with";
    }

    public int getInteger() {
        return integer;
    }

    public void setInteger(int integer) {
        this.integer = integer;
    }

    public long getLonger() {
        return longer;
    }

    public void setLonger(long longer) {
        this.longer = longer;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestSerialization that = (TestSerialization) o;

        if (integer != that.integer) return false;
        if (longer != that.longer) return false;
        if (string != null ? !string.equals(that.string) : that.string != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = integer;
        result = 31 * result + (int) (longer ^ (longer >>> 32));
        result = 31 * result + (string != null ? string.hashCode() : 0);
        return result;
    }
}
