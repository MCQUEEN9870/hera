# Email templates

Place HTML email templates here and load them from the classpath.

Guidelines (important for email client compatibility):
- Use **inline CSS** (avoid external CSS files).
- Do **not** include JavaScript.
- Use **absolute HTTPS** URLs for all images (email clients will not resolve relative URLs).
- Prefer PNG/JPG for broad client support (some clients do not display WebP reliably).

Suggested structure:
- premium-purchase.html (premium purchase confirmation)

If you host images on your website:
- Use URLs like: https://herapherigoods.in/attached_assets/.../your-image.png

If you host images on Supabase storage (public bucket):
- Use URLs like: https://<project>.supabase.co/storage/v1/object/public/<bucket>/premium/your-image.png
