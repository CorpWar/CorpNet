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
package net.corpwar.lib.corpnet.chat;

import java.io.Serializable;

/**
 * corpnet
 * Created by Ghost on 2014-12-13.
 */
public class Classes implements Serializable{

    static public class RegisterNick implements Serializable{
        public String nickname;
    }

    static public class AllNicks  implements Serializable{
        public String[] nicks;
    }

    static public class SendMessage implements Serializable{
        public String message;
    }
}
