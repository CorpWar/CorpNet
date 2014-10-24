package net.corpwar.lib.corpnet;

import java.util.Stack;

/**
 * GhostNet
 * Created by Ghost on 2014-10-09.
 */
public class SizedStack<T> extends Stack<T> {
    private int maxSize;

    public SizedStack(int size) {
        super();
        this.maxSize = size;
    }

    @Override
    public T push(T object) {
        //If the stack is too big, remove elements until it's the right size.
        while (this.size() > maxSize) {
            this.remove(0);
        }
        return super.push(object);
    }
}
