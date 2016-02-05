package net.corpwar.lib.corpnet.util;

import net.corpwar.lib.corpnet.NetworkPackage;

/**
 * corpnet
 * Created by Ghost on 2016-02-05.
 */
public class NetworkPackagePool extends ObjectPool<NetworkPackage> {

    @Override
    public NetworkPackage createExpensiveObject() {
        return new NetworkPackage();
    }
}
