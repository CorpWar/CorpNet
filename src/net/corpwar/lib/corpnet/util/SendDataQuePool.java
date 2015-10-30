package net.corpwar.lib.corpnet.util;

/**
 * corpnet
 * Created by Ghost on 2015-10-25.
 */
public class SendDataQuePool extends ObjectPool<SendDataQue> {

    @Override
    public SendDataQue createExpensiveObject() {
        return new SendDataQue();
    }
}
