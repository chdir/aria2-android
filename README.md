[![Gitter chat](https://badges.gitter.im/gitterHQ/gitter.png)](https://gitter.im/aria2-android/Lobby)

Android port of aria2
==========
This repository contains source code, required to build Android port of aria2 (x86 and armeabi
architectures, both PIC and non-PIC). The port includes frontend application and Android Service,
written in Java, as well as binaries of aria2, cross-compiled for above-listed architectures.
For details of how those binaries are launched by the Android application see manual of
[RootCommands][1] (no root access is needed).

[Google Play: store page](https://play.google.com/store/apps/details?id=net.sf.aria2)

[Google Play: early access builds](https://play.google.com/apps/testing/net.sf.aria2)

[Donate via PayPal](http://aria2-android.sf.net/donate.php)

Wait, what?
==========
[aria2][2] is a powerful [CLI][3] BitTorrent client, with support for HTTP/HTTPS, FTP and
Metalink (not available here) protocols. This port provides user-friendly way to launch aria2
daemon from easily installable Android application. It does not include any means to control
the client, add/stop/remove/pause torrent downloads etc. - install one of following frontends
to do that:

* http://www.transdroid.org/
* https://github.com/ziahamza/webui-aria2
* https://github.com/binux/yaaw

Rationale
==========
Upstream aria2 developer provides [aria2 binaries][4] for ARM architecture, but those don't come
handy, unless you are ready to manually launch commands in terminal. There is also no way to
get PIC/non-PIC versions and x86 versions of aria2. But, most importantly, the Linux console app
lacks integration with Android system; no notifications, no sane defaults, no way to start/stop
the process in one click. This project attempts to fix those issues.

Building from sources
==========

````bash
git clone --recursive "https://github.com/Alexander--/aria2-android"
cd aria2-android
export ANDROID_NDK="/path/to/android-ndk"
echo "ndk.dir=/path/to/android-ndk" > local.properties
./gradlew renameExecutables
./gradlew assembleDebug
````

If you have variable `ANDROID_HOME` set up and the directory in question is writable by user, the build 
will autimatically install correct SDK and tools versions there. Otherwise you have to do that yourself 
beforehand.

Current state
==========

[![Download from Sourceforge](https://img.shields.io/sourceforge/dt/aria2-android.svg)](http://sf.net/p/aria2-android/get/)

This port is generally usable, but some convenient features (such as ability to change default
aria2 arguments) are missing. Stay tuned with development!

TODO
==========
- [ ] Communicate with daemon over RPC to provide some services on-fly.
- [ ] Integrate basic wakelock/network lock support into the daemon, using websocket callbacks
- [ ] Async DNS resolution is disabled (see [upstream notes][5]), and there does not seem to be an
easy way to fix it in launcher besides using RPC to supply values during each network change
(see above).

FAQ
==========

* Why does aria2 sometimes display misleading status ("there may have been errors") in notifications?

When aria2 (or any other Android application) dies from signal (gets killed by systems etc.), it's
exit code (that contains information about last error) may be replaced by some rubbish.
This should not _normally_ happen during casual execution, but may occur when something in the system
kills aria2 process or because of (virtually nonexistent) bugs in aria2 itself. One tough example of
former, is when aria2 process takes it's time to stop current downloads, "stop aria2" switch times out,
and the user hits said switch again, killing aria2 process for good. There is no way to understand if
everything was really okay in this case.

* Why does Lollipop (aka Android 5.0) firmware prints a message about security issues when aria2 is started
with output logging on?

This is a harmless and expected behavior, the solution is currently [being searched for](#1).

This message is caused by specifics of x86 platform and implies, that _possible_ bugs in
aria2 are slightly more likely be exploited by potential hackers. Fortunately aria2 is rather
secure and constantly maintained application – it is much, MUCH less likely to be exploited,
compared to your (likely already outdated and vulnerable) device firmware.

License
==========
This application is licensed under GPLv3 (except for OpenSSL, which has it's own license); see
COPYING and license headers in individual Git submodules for details.

[1]: https://github.com/dschuermann/superuser-commands
[2]: http://sourceforge.net/projects/aria2/files/stable/
[3]: https://en.wikipedia.org/wiki/Command-line_interface
[4]: http://aria2.sf.net/
[5]: https://github.com/tatsuhiro-t/aria2/blob/master/README.android
