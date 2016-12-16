package net.corpwar.lib.corpnet.master;

import java.io.Serializable;

/**
 * corpnet
 * Created by Ghost on 2016-12-16.
 */
public class TestNat implements Serializable {

    private Boolean workingNat;

    public TestNat() {
        workingNat = false;
    }

    public TestNat(Boolean workingNat) {
        this.workingNat = workingNat;
    }

    public Boolean getWorkingNat() {
        return workingNat;
    }
}
