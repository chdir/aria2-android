/*
 * aria2 - The high speed download utility (Android port)
 *
 * Copyright © 2017 Alexander Rvachev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library under certain conditions as described in each
 * individual source file, and distribute linked combinations
 * including the two.
 * You must obey the GNU General Public License in all respects
 * for all of the code used other than OpenSSL.  If you modify
 * file(s) with this exception, you may extend this exception to your
 * version of the file(s), but you are not obligated to do so.  If you
 * do not wish to do so, delete this exception statement from your
 * version.  If you delete this exception statement from all source
 * files in the program, then also delete it here.
 */
package net.sf.aria2.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public final class InterfaceUtil {
    public static NetworkInterface resolveInterfaceByName(String string) {
        try {
            NetworkInterface found = NetworkInterface.getByName(string);

            if (found != null) {
                return found;
            }

            Enumeration<NetworkInterface>  interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface ifc = interfaces.nextElement();

                Enumeration<InetAddress> addrs = ifc.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress address = addrs.nextElement();

                    if (string.equals(address.getHostAddress())) {
                        return ifc;
                    }
                }
            }

            // TODO consider matching the provided string agains hostname
            // (can't do it on the main thread tho…)
        } catch (SocketException se) {
            // ok
        }

        return null;
    }

    public static String getInterfaceAddress(NetworkInterface iface) {
        if (iface == null) return null;

        Enumeration<InetAddress> addrs = iface.getInetAddresses();
        if (addrs.hasMoreElements()) {
            final InetAddress firstAddr = addrs.nextElement();

            return firstAddr.getHostAddress();
        }

        return null;
    }
}
