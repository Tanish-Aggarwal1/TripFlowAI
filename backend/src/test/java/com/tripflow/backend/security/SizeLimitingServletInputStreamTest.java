package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.exception.PayloadTooLargeException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

class SizeLimitingServletInputStreamTest {

	private static final long MAX_BYTES = 10;

	@Test
	void bodyWithinLimit_readsFully() throws Exception {
		SizeLimitingServletInputStream stream = wrap(new byte[(int) MAX_BYTES]);

		byte[] buf = new byte[4];
		int total = 0;
		int n;
		while ((n = stream.read(buf)) != -1) {
			total += n;
		}

		assertThat(total).isEqualTo(MAX_BYTES);
	}

	@Test
	void bodyOverLimit_throwsPayloadTooLargeMidRead_regardlessOfDeclaredContentLength() {
		// This is the scenario RequestSizeLimitFilter's own upfront Content-Length check can't
		// catch: a body that turns out longer than declared (or with no length declared at all,
		// e.g. chunked transfer encoding). Only counting bytes as they're actually read catches it.
		SizeLimitingServletInputStream stream = wrap(new byte[(int) MAX_BYTES + 50]);

		assertThatThrownBy(() -> {
			byte[] buf = new byte[4];
			while (stream.read(buf) != -1) {
				// drain until it throws
			}
		}).isInstanceOf(PayloadTooLargeException.class);
	}

	@Test
	void singleByteRead_alsoEnforcesLimit() {
		SizeLimitingServletInputStream stream = wrap(new byte[(int) MAX_BYTES + 1]);

		assertThatThrownBy(() -> {
			int read = 0;
			while (stream.read() != -1) {
				read++;
			}
		}).isInstanceOf(PayloadTooLargeException.class);
	}

	private SizeLimitingServletInputStream wrap(byte[] content) {
		return new SizeLimitingServletInputStream(new FakeServletInputStream(content), MAX_BYTES);
	}

	/** Minimal ServletInputStream over a byte array — enough to drive the class under test
	 * without needing a real servlet container or MockHttpServletRequest's content-length
	 * bookkeeping getting in the way. */
	private static final class FakeServletInputStream extends ServletInputStream {
		private final ByteArrayInputStream delegate;

		FakeServletInputStream(byte[] content) {
			this.delegate = new ByteArrayInputStream(content);
		}

		@Override
		public int read() {
			return delegate.read();
		}

		@Override
		public int read(byte[] b, int off, int len) {
			return delegate.read(b, off, len);
		}

		@Override
		public boolean isFinished() {
			return delegate.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			throw new UnsupportedOperationException();
		}
	}
}
