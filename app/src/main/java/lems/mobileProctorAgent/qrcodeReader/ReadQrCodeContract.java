package lems.mobileProctorAgent.qrcodeReader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ReadQrCodeContract extends ActivityResultContract<Void, Uri> {
    public final static String QRCODE_URI = "lems.mobileProctorAgent.qrcodeReader.ReadQrCodeContract.QRCODE_URI";

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, Void input) {
        Intent intent = new Intent(context, QRCodeReaderActivity.class);
        return intent;
    }

    @Override
    public Uri parseResult(int resultCode, @Nullable Intent result) {
        if (resultCode != Activity.RESULT_OK || result == null) {
            return null;
        }
        return result.getParcelableExtra(QRCODE_URI);
    }

    public static Intent createReturnedIntent(Uri uri) {
        Intent intent = new Intent();
        intent.putExtra(QRCODE_URI, uri);
        return intent;
    }
}
