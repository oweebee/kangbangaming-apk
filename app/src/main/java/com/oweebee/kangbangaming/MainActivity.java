package com.oweebee.kangbangaming;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String PREF_FILE = "kangban_prefs";
    private static final String PREF_URL  = "server_url";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NE PAS mettre le fullscreen ici — l'écran de setup a besoin du comportement par défaut
        // (clavier qui pousse le contenu vers le haut).
        // Le fullscreen est appliqué uniquement dans loadWebView().

        String savedUrl = prefs().getString(PREF_URL, null);
        if (savedUrl == null || savedUrl.isEmpty()) {
            showSetup();
        } else {
            loadWebView(savedUrl);
        }
    }

    // ── Écran de configuration (premier lancement) ────────────────────────────

    private void showSetup() {
        // Comportement fenêtre par défaut — le clavier pousse normalement le contenu
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#242424"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(80), dp(32), dp(64));

        TextView title = new TextView(this);
        title.setText("KangBanGaming");
        title.setTextColor(Color.parseColor("#f0ece4"));
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Entrez l'adresse de votre serveur");
        sub.setTextColor(Color.parseColor("#a09890"));
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(32));
        root.addView(sub);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://");
        urlInput.setHintTextColor(Color.parseColor("#505050"));
        urlInput.setTextColor(Color.parseColor("#f0ece4"));
        urlInput.setBackgroundColor(Color.parseColor("#2e2e2e"));
        urlInput.setTextSize(15);
        urlInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        urlInput.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_URI
        );
        urlInput.setText("https://");

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.bottomMargin = dp(16);
        root.addView(urlInput, inputParams);

        Button btn = new Button(this);
        btn.setText("Connexion");
        btn.setTextColor(Color.parseColor("#f0ece4"));
        btn.setBackgroundColor(Color.parseColor("#C0570A"));
        btn.setTextSize(15);
        btn.setPadding(0, dp(12), 0, dp(12));

        root.addView(btn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        btn.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (url.isEmpty() || url.equals("https://")) {
                urlInput.setError("Entrez une URL valide");
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            prefs().edit().putString(PREF_URL, url).apply();
            loadWebView(url);
        });

        setContentView(root);
    }

    // ── WebView ───────────────────────────────────────────────────────────────

    private void loadWebView(String url) {
        // Fullscreen uniquement ici, pour le WebView
        applyFullscreen();

        webView = new WebView(this);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        // Pas de scrollbar horizontal
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(true);

        // Android 11+ : le clavier envoie des IME insets au lieu de redimensionner la fenêtre.
        // Sans ce listener, le clavier recouvre le contenu. Ce listener ajuste le padding bas
        // du WebView selon la hauteur du clavier.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            webView.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
                int navHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                // Quand le clavier est ouvert : padding = hauteur clavier
                // Quand fermé : padding = hauteur barre nav (0 si navigation gestuelle)
                v.setPadding(0, 0, 0, imeHeight > 0 ? imeHeight : navHeight);
                return insets;
            });
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                // Délai 500ms : React a besoin d'un tick pour rendre le DOM réel avant injection
                view.postDelayed(() -> injectViewportFix(view), 500);
            }
        });

        // Sans WebChromeClient, window.confirm / window.alert / window.prompt sont muets.
        // Les boutons "Supprimer une note" utilisent window.confirm — ils ne fonctionneraient pas.
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.confirm())
                    .show();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                    .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
                return true;
            }
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
                final android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
                return true;
            }
        });

        // Appui long → changer d'URL
        webView.setOnLongClickListener(v -> {
            showUrlChangeDialog();
            return true;
        });

        webView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(webView);
        webView.loadUrl(url);
    }

    // ── Injection JS : force la largeur à device-width ───────────────────────

    private void injectViewportFix(WebView view) {
        view.evaluateJavascript(
            "(function() {" +
            "  var meta = document.querySelector('meta[name=viewport]');" +
            "  if (!meta) {" +
            "    meta = document.createElement('meta');" +
            "    meta.name = 'viewport';" +
            "    if (document.head) document.head.appendChild(meta);" +
            "  }" +
            "  meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';" +
            "  if (document.documentElement) document.documentElement.style.maxWidth = '100vw';" +
            "  if (document.body) {" +
            "    document.body.style.maxWidth = '100vw';" +
            "    document.body.style.overflowX = 'hidden';" +
            "  }" +
            "})()",
            null
        );
    }

    // ── Fullscreen adaptatif ──────────────────────────────────────────────────

    private void applyFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ : edge-to-edge + masquer barres système
            // NE PAS utiliser SOFT_INPUT_ADJUST_PAN ici — il est ignoré sur API 30+
            // Le listener IME insets sur le WebView prend en charge le clavier.
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController ctrl = getWindow().getInsetsController();
            if (ctrl != null) {
                ctrl.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                ctrl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Android 8-10 : méthode classique
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    // ── Dialog changement d'URL ───────────────────────────────────────────────

    private void showUrlChangeDialog() {
        String current = prefs().getString(PREF_URL, "");

        EditText input = new EditText(this);
        input.setText(current);
        input.setTextColor(Color.parseColor("#f0ece4"));
        input.setBackgroundColor(Color.parseColor("#2e2e2e"));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        new AlertDialog.Builder(this)
            .setTitle("Changer de serveur")
            .setView(input)
            .setPositiveButton("Connexion", (dialog, which) -> {
                String newUrl = input.getText().toString().trim();
                if (!newUrl.isEmpty()) {
                    if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                        newUrl = "https://" + newUrl;
                    }
                    prefs().edit().putString(PREF_URL, newUrl).apply();
                    loadWebView(newUrl);
                }
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    // ── Bouton retour ─────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Quitter")
                .setMessage("Quitter KangBanGaming ?")
                .setPositiveButton("Quitter", (d, w) -> finish())
                .setNegativeButton("Annuler", null)
                .show();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF_FILE, MODE_PRIVATE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
