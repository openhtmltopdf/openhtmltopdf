package com.openhtmltopdf.extend;

public interface FSSupplier<T> {
	/**
	 * Returned by {@link #lastModified()} if the age of the supplied resource is unknown.
	 */
	public static final long UNKNOWN_LAST_MODIFIED = 0;

	public T supply();

	/**
	 * A key that uniquely identifies the resource this supplier supplies, typically the absolute
	 * URI it is loaded from. Used by caches such as
	 * {@link com.openhtmltopdf.outputdevice.helper.FontCache}.
	 *
	 * <p>The key has to be absolute: a relative URI such as {@code font.ttf} refers to different
	 * resources depending on the document it appears in. Return null - the default - if no such
	 * key can be provided, in which case the resource is not cached.</p>
	 */
	public default String cacheKey() {
		return null;
	}

	/**
	 * When the supplied resource was last modified, or {@link #UNKNOWN_LAST_MODIFIED} - the
	 * default - if that can not be determined. A cached resource is reloaded whenever this value
	 * changes, so implementations may return any value that changes with the resource.
	 */
	public default long lastModified() {
		return UNKNOWN_LAST_MODIFIED;
	}
}
