package net.sf.aria2;

import android.content.res.Resources;

public enum ExitCode {
    // success codes
    Success(0, true, R.string.all_finished),
    SomeUnfinished(7, true, R.string.there_are_unfinished),

    // error codes
    Unknown(1, R.string.unknown_err),

    // expected argument errors
    BadArgument(28, R.string.invalid_arg),

    // these may potentially prevent aria2 from starting
    NetworkErr(6, R.string.netw_err),

    DiskErr(9, R.string.insuff_disc),

    FileExists(13, R.string.file_exists),
    RenamingFailed(14, R.string.rename_failed),
    OpenFailed(15,  R.string.open_failed),
    TruncateFailed(16, R.string.trunc_failed),

    FileIOErr(17, R.string.io_err),
    MkdirErr(18, R.string.mkdir_err),

    // no other error should cause session failures

    // other expected errors: control errors (not really errors!)
    // this caused and handled by the frontend, so no need to bother user with them
    FileAlreadyIn(11, R.string.other_err),
    TorrentAlreadyIn(12, R.string.other_err),

    NameResolvErr(19, R.string.other_err),
    MetalinkInvalid(20, R.string.other_err),

    BencodedCorrupted(25, R.string.other_err),
    TorrentInvalid(26, R.string.other_err),
    InvalidMagnet(27, R.string.other_err),

    RPCErr(30, R.string.other_err),

    // other expected errors: download errors
    Timeout(2, R.string.download_err),
    NotFound(3, R.string.download_err),
    MaxNotFound(4, R.string.download_err),
    SpeedLimit(5, R.string.download_err),

    ResumeFailed(8, R.string.download_err),

    PieceLengthChanged(10, R.string.download_err),

    FTPFail(21, R.string.download_err),
    RespHdrErr(22, R.string.download_err),
    ManyRedirects(23, R.string.download_err),
    HttpAuthErr(24, R.string.download_err),

    ServerErr(29, R.string.download_err),

    // whut!?
    UnsupportedCode(-1, R.string.unknown_err);

    private final int code;
    private final boolean success;
    private final int translation;

    ExitCode(int code, int translation) {
        this(code, false, translation);
    }

    ExitCode(int code, boolean success, int translation) {
        this.code = code;
        this.success = success;
        this.translation = translation;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getCode() {
        return code;
    }

    public String getDesc(Resources r) {
        return r.getString(translation);
    }

    @Override
    public String toString() {
        return name() + ' ' + code;
    }

    public static ExitCode from(int errorCode) {
        for (ExitCode exitCode: ExitCode.values()) {
            if (exitCode.code == errorCode && exitCode != UnsupportedCode)
                return exitCode;
        }

        return UnsupportedCode;
    }
}
