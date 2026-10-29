package cn.dancingsnow.neoecoae.api.me.provider;

/** Test-owned uncertainty signal; this is not a bundled optional-mod API. */
public final class ECOIndeterminateBatchException extends RuntimeException {
    public ECOIndeterminateBatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
