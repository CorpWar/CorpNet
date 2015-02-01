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
package net.corpwar.lib.corpnet;

import java.util.Arrays;

public class SplitMessage implements Comparable<SplitMessage>{

    private int sequenceId;
    private byte[] data;
    private long createTime;

    public SplitMessage(int sequenceId, byte[] data) {
        this.sequenceId = sequenceId;
        this.data = data;
        createTime = System.currentTimeMillis();
    }

    public Integer getSequenceId() {
        return sequenceId;
    }

    public byte[] getData() {
        return data;
    }

    public long getCreateTime() {
        return createTime;
    }

    @Override
    public int compareTo(SplitMessage o) {
        return this.sequenceId > o.sequenceId ? -1 : this.sequenceId < o.sequenceId ? 1 : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SplitMessage that = (SplitMessage) o;

        if (sequenceId != that.sequenceId) return false;
        if (!Arrays.equals(data, that.data)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = sequenceId;
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
