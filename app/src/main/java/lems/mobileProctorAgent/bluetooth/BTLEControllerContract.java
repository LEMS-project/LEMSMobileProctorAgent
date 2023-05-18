package lems.mobileProctorAgent.bluetooth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BTLEControllerContract extends ActivityResultContract<Void, String> {
    public final static String CONTROLLER_MAC = "lems.mobileProctorAgent.bluetooth.BTLEControllerContract.CONTROLLER_MAC";

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, Void input) {
        Intent intent = new Intent(context, BTLEControllerSelectionActivity.class); //BTControllerSelectionActivity.class);
        return intent;
    }

    @Override
    public String parseResult(int resultCode, @Nullable Intent result) {
        if (resultCode != Activity.RESULT_OK || result == null) {
            return null;
        }
        return result.getStringExtra(CONTROLLER_MAC);
    }

    public static Intent createReturnedIntent(String controllerMacAddress) {
        Intent intent = new Intent();
        intent.putExtra(CONTROLLER_MAC, controllerMacAddress);
        return intent;
    }
}
