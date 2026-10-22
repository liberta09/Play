with open('app/src/main/AndroidManifest.xml', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace the second exported="true" (for CloudflareVerifyActivity) back to false
cf_bad = '''        <!-- Cloudflare Turnstile / interaktif bot doğrulama ekranı -->
        <activity
            android:name=".ui.cloudflare.CloudflareVerifyActivity"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|keyboard|navigation|uiMode"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="play" android:host="web" />
            </intent-filter>
        </activity>'''

cf_good = '''        <!-- Cloudflare Turnstile / interaktif bot doğrulama ekranı -->
        <activity
            android:name=".ui.cloudflare.CloudflareVerifyActivity"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|keyboard|navigation|uiMode"
            android:exported="false" />'''

# Normalize newlines
text = text.replace('\r\n', '\n')
cf_bad = cf_bad.replace('\r\n', '\n')
cf_good = cf_good.replace('\r\n', '\n')

if cf_bad in text:
    text = text.replace(cf_bad, cf_good)
    with open('app/src/main/AndroidManifest.xml', 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print("SUCCESS")
else:
    print("NOT FOUND")
