# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.1.x (io.github.openhtmltopdf) | :white_check_mark: |
| 1.0.x (com.openhtmltopdf) | :x: Unmaintained |

## Reporting a Vulnerability

Please **do not** report security vulnerabilities through public GitHub Issues.

Use GitHub's private vulnerability reporting instead:
1. Go to the [Security tab](../../security) of this repository
2. Click **"Report a vulnerability"**
3. Fill in the details and submit

We will acknowledge receipt within 7 days and aim to provide a fix or mitigation within 90 days.

## Security Considerations

openhtmltopdf renders HTML to PDF and **fetches external resources** (CSS, images, fonts) during rendering.

**If your application renders user-supplied HTML**, be aware that:

- External resource URLs in the HTML will be fetched by the renderer
- HTTP redirects are followed automatically by the underlying `HttpURLConnection`
- The default `NaiveUserAgent` applies no URL filtering

**Recommended mitigations:**
- Only render trusted HTML input
- Configure `AccessController` to restrict fetchable URLs to an allowlist
- Consider disabling external resource loading entirely for untrusted input

## Scope

Vulnerabilities in the following areas are in scope:
- Resource loading (`NaiveUserAgent`, `FSStreamFactory`)
- XML/SVG parsing (Batik integration)
- PDF output handling
