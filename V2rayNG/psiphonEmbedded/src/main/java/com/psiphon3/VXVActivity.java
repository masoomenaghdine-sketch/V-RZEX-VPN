package com.psiphon3;

import android.widget.Toast;

import com.psiphon3.psiphonlibrary.TunnelServiceInteractor;

import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * VØRZEX entry point for the embedded Psiphon client.
 *
 * The user must leave this screen through Back to return to VØRZEX. We stop the
 * Psiphon tunnel and wait for its stopped state before finishing, so Xray is not
 * restarted while Psiphon still owns Android's VPN slot.
 */
public final class VXVActivity extends MainActivity {
    private boolean stoppingTunnel;
    private Disposable stopDisposable;

    @Override
    public void onBackPressed() {
        if (stoppingTunnel) return;

        TunnelServiceInteractor interactor = getTunnelServiceInteractor();
        if (interactor == null) {
            super.onBackPressed();
            return;
        }

        stoppingTunnel = true;
        stopDisposable = interactor.tunnelStateFlowable()
                .filter(state -> !state.isUnknown() && !state.isRunning())
                .take(1)
                .timeout(10, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        state -> finishAfterTunnelStops(),
                        error -> {
                            stoppingTunnel = false;
                            Toast.makeText(
                                    VXVActivity.this,
                                    "VXV is still stopping. Please wait and try Back again.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );

        interactor.stopTunnelService();
    }

    private void finishAfterTunnelStops() {
        stoppingTunnel = false;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (stopDisposable != null) {
            stopDisposable.dispose();
            stopDisposable = null;
        }
        super.onDestroy();
    }
}
