package com.folioreader.ui.folio.fragment;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.bossturban.webviewmarker.TextSelectionSupport;
import com.folioreader.Config;
import com.folioreader.Constants;
import com.folioreader.R;
import com.folioreader.model.Highlight;
import com.folioreader.model.event.MediaOverlayPlayPauseEvent;
import com.folioreader.model.event.MediaOverlaySpeedEvent;
import com.folioreader.model.event.ReloadDataEvent;
import com.folioreader.model.event.RewindIndexEvent;
import com.folioreader.model.event.WebViewPosition;
import com.folioreader.model.media_overlay.OverlayItems;
import com.folioreader.model.quickaction.ActionItem;
import com.folioreader.model.quickaction.QuickAction;
import com.folioreader.model.sqlite.HighLightTable;
import com.folioreader.ui.base.HtmlTask;
import com.folioreader.ui.base.HtmlTaskCallback;
import com.folioreader.ui.base.HtmlUtil;
import com.folioreader.ui.folio.activity.FolioActivity;
import com.folioreader.util.AppUtil;
import com.folioreader.util.HighlightUtil;
import com.folioreader.util.UiUtil;
import com.folioreader.view.ObservableWebView;
import com.folioreader.view.VerticalSeekbar;
import com.squareup.otto.Subscribe;

import org.readium.r2_streamer.model.publication.SMIL.Clip;
import org.readium.r2_streamer.model.publication.SMIL.MediaOverlays;
import org.readium.r2_streamer.model.publication.link.Link;
import org.readium.r2_streamer.parser.EpubParser;
import org.readium.r2_streamer.parser.EpubParserException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mahavir on 4/2/16.
 */
public class FolioPageFragment extends Fragment implements HtmlTaskCallback {

    public static final String KEY_FRAGMENT_FOLIO_POSITION = "com.folioreader.ui.folio.fragment.FolioPageFragment.POSITION";
    public static final String KEY_FRAGMENT_FOLIO_BOOK_TITLE = "com.folioreader.ui.folio.fragment.FolioPageFragment.BOOK_TITLE";
    public static final String KEY_FRAGMENT_EPUB_FILE_NAME = "com.folioreader.ui.folio.fragment.FolioPageFragment.EPUB_FILE_NAME";
    private static final String KEY_IS_SMIL_AVAILABLE = "com.folioreader.ui.folio.fragment.FolioPageFragment.IS_SMIL_AVAILABLE";
    public static final String TAG = FolioPageFragment.class.getSimpleName();

    private static final int ACTION_ID_COPY = 1001;
    private static final int ACTION_ID_SHARE = 1002;
    private static final int ACTION_ID_HIGHLIGHT = 1003;
    private static final int ACTION_ID_DEFINE = 1004;

    private static final int ACTION_ID_HIGHLIGHT_COLOR = 1005;
    private static final int ACTION_ID_DELETE = 1006;

    private static final int ACTION_ID_HIGHLIGHT_YELLOW = 1007;
    private static final int ACTION_ID_HIGHLIGHT_GREEN = 1008;
    private static final int ACTION_ID_HIGHLIGHT_BLUE = 1009;
    private static final int ACTION_ID_HIGHLIGHT_PINK = 1010;
    private static final int ACTION_ID_HIGHLIGHT_UNDERLINE = 1011;
    private static final String KEY_TEXT_ELEMENTS = "text_elements";
    private static final String SPINE_ITEM = "spine_item";
    private WebViewPosition mWebviewposition;
    private String mHtmlString = null;
    private boolean hasMediaOverlay = false;

    public interface FolioPageFragmentCallback {

        void hideOrshowToolBar();

        void hideToolBarIfVisible();

        void setPagerToPosition(String href);

        void setLastWebViewPosition(int position);
    }

    private View mRootView;

    private VerticalSeekbar mScrollSeekbar;
    private ObservableWebView mWebview;
    private TextSelectionSupport mTextSelectionSupport;
    private TextView mPagesLeftTextView, mMinutesLeftTextView;
    private FolioPageFragmentCallback mActivityCallback;

    private int mScrollY;
    private int mTotalMinutes;
    private String mSelectedText;
    private boolean mIsSpeaking = true;
    private Map<String, String> mHighlightMap;
    private Animation mFadeInAnimation, mFadeOutAnimation;

    private Link spineItem;
    private int mPosition = -1;
    private String mBookTitle;
    private String mEpubFileName = null;
    private boolean mIsSmilAvailable;
    private int mPos;
    private boolean mIsPageReloaded;
    private int mLastWebviewScrollpos;

    //**********************************//
    //          MEDIA OVERLAY           //
    //**********************************//
    private MediaOverlays mediaOverlays;
    private List<OverlayItems> mediaItems;
    private int mediaItemPosition = 0;
    private MediaPlayer mediaPlayer;

    private Clip currentClip;

    private boolean isMediaPlayerReady;
    private Handler mediaHandler;

    //*********************************//
    //              TTS                //
    //*********************************//
    private TextToSpeech mTextToSpeech;
    private boolean isSpeaking = false;

    public static FolioPageFragment newInstance(int position, String bookTitle, Link spineRef) {
        FolioPageFragment fragment = new FolioPageFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_FRAGMENT_FOLIO_POSITION, position);
        args.putString(KEY_FRAGMENT_FOLIO_BOOK_TITLE, bookTitle);
        args.putSerializable(SPINE_ITEM, spineRef);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        if ((savedInstanceState != null)
                && savedInstanceState.containsKey(KEY_FRAGMENT_FOLIO_POSITION)
                && savedInstanceState.containsKey(KEY_FRAGMENT_FOLIO_BOOK_TITLE)) {
            mPosition = savedInstanceState.getInt(KEY_FRAGMENT_FOLIO_POSITION);
            mBookTitle = savedInstanceState.getString(KEY_FRAGMENT_FOLIO_BOOK_TITLE);
            mEpubFileName = savedInstanceState.getString(KEY_FRAGMENT_EPUB_FILE_NAME);
            spineItem = (Link) savedInstanceState.getSerializable(SPINE_ITEM);
        } else {
            mPosition = getArguments().getInt(KEY_FRAGMENT_FOLIO_POSITION);
            mBookTitle = getArguments().getString(KEY_FRAGMENT_FOLIO_BOOK_TITLE);
            mEpubFileName = getArguments().getString(KEY_FRAGMENT_EPUB_FILE_NAME);
            spineItem = (Link) getArguments().getSerializable(SPINE_ITEM);
        }
        mediaItems = new ArrayList<>();
        isMediaPlayerReady = false;
        if (spineItem != null) {
            if (spineItem.properties.contains("media-overlay")) {
                mediaOverlays = spineItem.mediaOverlay;
                hasMediaOverlay = true;
            }
        }
        mRootView = View.inflate(getActivity(), R.layout.folio_page_fragment, null);
        mPagesLeftTextView = (TextView) mRootView.findViewById(R.id.pagesLeft);
        mMinutesLeftTextView = (TextView) mRootView.findViewById(R.id.minutesLeft);
        if (getActivity() instanceof FolioPageFragmentCallback)
            mActivityCallback = (FolioPageFragmentCallback) getActivity();

        FolioActivity.BUS.register(this);

        mTextToSpeech = new TextToSpeech(getActivity(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    mTextToSpeech.setLanguage(Locale.UK);
                    mTextToSpeech.setSpeechRate(0.70f);
                }

                mTextToSpeech.setOnUtteranceCompletedListener(
                        new TextToSpeech.OnUtteranceCompletedListener() {
                            @Override
                            public void onUtteranceCompleted(String utteranceId) {
                                getActivity().runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        if (isSpeaking) {
                                            //removeTTS
                                            mWebview.loadUrl("javascript:alert(removeTTS())");
                                            speakAudio();
                                        }
                                    }
                                });
                            }
                        });
            }
        });

        initSeekbar();
        initAnimations();
        initWebView();
        updatePagesLeftTextBg();
        return mRootView;
    }

    private String getWebviewUrl() {
        return Constants.LOCALHOST + mBookTitle + "/" + spineItem.href;
    }

    public void speakAudio() {
        if (mediaItemPosition < mediaItems.size()) {
            isSpeaking = true;
            HashMap<String, String> params = new HashMap<>();
            OverlayItems items = mediaItems.get(mediaItemPosition);
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "stringId");
            mWebview.loadUrl((String.format("javascript:alert(highlightTTS('%s'))", escape(items.getText()))));
            mTextToSpeech.speak(items.getText(), TextToSpeech.QUEUE_FLUSH, params);
            mediaItemPosition++;
        }
    }

    private String escape(String str) {
        String a = str.replace("\"", "\\\"");
        return a.replace("\'", "\\'");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float positionTopView = mWebview.getTop();
        float contentHeight = mWebview.getContentHeight();
        float currentScrollPosition = mScrollY;
        float percentWebview = (currentScrollPosition - positionTopView) / contentHeight;
        float webviewsize = mWebview.getContentHeight() - mWebview.getTop();
        float positionInWV = webviewsize * percentWebview;
        int positionY = Math.round(mWebview.getTop() + positionInWV);
        mScrollY = positionY;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void pauseButtonClicked(MediaOverlayPlayPauseEvent event) {
        if (isAdded()) {
            if (spineItem.href.equals(event.getHref())) {
                if (event.isStateChanged()) {
                    if (mediaPlayer != null) {
                        mediaPlayer.pause();
                    }
                    if (mTextToSpeech != null) {
                        if (mTextToSpeech.isSpeaking()) {
                            mTextToSpeech.stop();
                        }
                    }
                } else {
                    if (spineItem.typeLink.equals("application/xhtml+xml")) {
                        if (hasMediaOverlay) {
                            if (event.isPlay()) {
                                UiUtil.keepScreenAwake(true, getActivity());
                                startPlaying();
                            } else {
                                UiUtil.keepScreenAwake(false, getActivity());
                                pausePlaying();
                            }
                        } else {
                            if (event.isPlay()) {
                                UiUtil.keepScreenAwake(true, getActivity());
                                speakAudio();
                            } else {
                                UiUtil.keepScreenAwake(false, getActivity());
                                isSpeaking = false;
                                mTextToSpeech.stop();
                            }
                        }
                    } else {
                        Toast.makeText(getActivity(), "Audio not available for this page", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @TargetApi(Build.VERSION_CODES.M)
    @Subscribe
    public void speedChanged(MediaOverlaySpeedEvent event) {
        if (mediaPlayer != null) {
            switch (event.getSpeed()) {
                case HALF:
                    //TODO mediaPlayer.getPlaybackParams().setSpeed(0.5f);
                    break;
                case ONE:
                    //TODO mediaPlayer.getPlaybackParams().setSpeed(1.0f);
                    break;
                case ONE_HALF:
                    //TODO mediaPlayer.getPlaybackParams().setSpeed(1.5f);
                    break;
                case TWO:
                    //TODO mediaPlayer.getPlaybackParams().setSpeed(2.0f);
                    break;
            }
        }
    }

    private void pausePlaying() {
        if (isMediaPlayerReady && mediaPlayer != null) {
            mediaPlayer.pause();
        }
        if (mTextToSpeech != null && mTextToSpeech.isSpeaking()) {
            mTextToSpeech.stop();
        }
    }

    private void startPlaying() {
        if (isMediaPlayerReady && mediaPlayer != null) {
            currentClip = mediaOverlays.clip(mediaItems.get(mediaItemPosition).getId());
            if (currentClip != null) {
                mediaPlayer.start();
                mediaHandler.post(mHighlightTask);
            } else {
                mediaItemPosition++;
                mediaPlayer.start();
                mediaHandler.post(mHighlightTask);
            }
        }
    }

    @Subscribe
    public void reload(ReloadDataEvent reloadDataEvent) {
        if (isAdded()) {
            mLastWebviewScrollpos = mWebview.getScrollY();
            mIsPageReloaded = true;
            setHtml(true);
        }
    }

    @Override
    public void onReceiveHtml(String html) {
        if (isAdded()) {
            mHtmlString = html;
            setHtml(false);
        }
    }

    private void setHtml(boolean reloaded) {
        if (spineItem != null) {
            String ref = spineItem.href;
            if (!reloaded) {
                if (spineItem.properties.contains("media-overlay")) {
                    parseSMIL(mHtmlString);
                } else {
                    parseSMILForTTS(mHtmlString);
                }
            }
            String path = ref.substring(0, ref.lastIndexOf("/"));
            mWebview.loadDataWithBaseURL(
                    Constants.LOCALHOST + mBookTitle + "/" + path + "/",
                    HtmlUtil.getHtmlContent(getActivity(), mHtmlString, mBookTitle),
                    "text/html",
                    "UTF-8",
                    null);
        }
    }

    private Runnable mHighlightTask = new Runnable() {
        @Override
        public void run() {
            int currentPosition = mediaPlayer.getCurrentPosition();
            if (mediaPlayer.getDuration() != currentPosition) {
                if (mediaItemPosition < mediaItems.size()) {
                    int end = (int) currentClip.end * 1000;
                    if (currentPosition > end) {
                        mediaItemPosition++;
                        currentClip = mediaOverlays.clip(mediaItems.get(mediaItemPosition).getId());
                        if (currentClip != null) {
                            highLightText(mediaItems.get(mediaItemPosition).getId());
                        } else {
                            mediaItemPosition++;
                        }
                    }
                    mediaHandler.postDelayed(mHighlightTask, 10);
                } else {
                    mediaHandler.removeCallbacks(mHighlightTask);
                }
            }
        }
    };

    private void highLightText(String fragmentId) {
        mWebview.loadUrl(String.format(getString(R.string.audio_mark_id), fragmentId));
    }

    public void parseSMIL(String html) {
        try {
            mediaItems.clear();
            mediaItemPosition = 0;
            Document document = EpubParser.xmlParser(html);
            NodeList sections = document.getDocumentElement().getElementsByTagName("section");
            for (int i = 0; i < sections.getLength(); i++) {
                parseNodes(mediaItems, (Element) sections.item(i));
            }
            String audioFile = mediaOverlays.getAudioPath(spineItem.href);
            setUpPlayer(audioFile);
        } catch (EpubParserException e) {
            e.printStackTrace();
        }
    }

    private void parseNodes(List<OverlayItems> names, Element section) {
        for (Node n = section.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                if (e.hasAttribute("id")) {
                    names.add(new OverlayItems(e.getAttribute("id"), e.getTagName()));
                } else {
                    parseNodes(names, e);
                }
            }
        }
    }

    public void parseSMILForTTS(String html) {
        try {
            Document document = EpubParser.xmlParser(html);
            NodeList sections = document.getDocumentElement().getElementsByTagName("body");
            for (int i = 0; i < sections.getLength(); i++) {
                parseNodesTTS(mediaItems, (Element) sections.item(i));
            }
            Log.i("TTStest", "nodes = " + mediaItems.toString());
        } catch (EpubParserException e) {
            e.printStackTrace();
        }
    }

    private void parseNodesTTS(List<OverlayItems> names, Element section) {
        for (Node n = section.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                for (Node n1 = e.getFirstChild(); n1 != null; n1 = n1.getNextSibling()) {
                    if (n1.getTextContent() != null) {
                        OverlayItems i = new OverlayItems();
                        i.setText(n1.getTextContent());
                        i.setSpineHref(spineItem.href);
                        names.add(i);
                    }
                }
                parseNodesTTS(names, e);
            }
        }
    }

    private void setUpPlayer(String path) {
        mediaHandler = new Handler();
        try {
            mediaItemPosition = 0;
            String uri = Constants.LOCALHOST + mBookTitle + "/" + path;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(uri);
            mediaPlayer.prepare();
            isMediaPlayerReady = true;
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        //TODO save last media overlay item
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTextToSpeech != null) {
            if (mTextToSpeech.isSpeaking()) {
                mTextToSpeech.stop();
            }
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                mediaHandler.removeCallbacks(mHighlightTask);
            }
        }
    }

    private void initWebView() {
        mWebview = (ObservableWebView) mRootView.findViewById(R.id.contentWebView);
        mWebview.setFragment(FolioPageFragment.this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        mWebview.getViewTreeObserver().
                addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int height =
                                (int) Math.floor(mWebview.getContentHeight() * mWebview.getScale());
                        int webViewHeight = mWebview.getMeasuredHeight();
                        mScrollSeekbar.setMaximum(height - webViewHeight);
                    }
                });

        mWebview.getSettings().setJavaScriptEnabled(true);
        mWebview.setVerticalScrollBarEnabled(false);
        mWebview.getSettings().setAllowFileAccess(true);

        mWebview.setHorizontalScrollBarEnabled(false);

        mWebview.addJavascriptInterface(this, "Highlight");
        mWebview.setScrollListener(new ObservableWebView.ScrollListener() {
            @Override
            public void onScrollChange(int percent) {
                if (mWebview.getScrollY() != 0) {
                    mScrollY = mWebview.getScrollY();
                    ((FolioActivity) getActivity()).setLastWebViewPosition(mScrollY);
                }
                mScrollSeekbar.setProgressAndThumb(percent);
                updatePagesLeftText(percent);

            }
        });

        mWebview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isAdded()) {
                    view.loadUrl("javascript:alert(getReadingTime())");
                    view.loadUrl("javascript:alert(wrappingSentencesWithinPTags())");
                    view.loadUrl(String.format(getString(R.string.setmediaoverlaystyle),
                            Highlight.HighlightStyle.classForStyle(
                                    Highlight.HighlightStyle.Normal)));

                    if (mWebviewposition != null) {
                        setWebViewPosition(mWebviewposition.getWebviewPos());
                    } else if (isCurrentFragment()) {
                        setWebViewPosition(AppUtil.getPreviousBookStateWebViewPosition(getActivity(), mBookTitle));
                    } else if (mIsPageReloaded) {
                        setWebViewPosition(mLastWebviewScrollpos);
                        mIsPageReloaded = false;
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!url.isEmpty() && url.length() > 0) {
                    if (Uri.parse(url).getScheme().startsWith("highlight")) {
                        final Pattern pattern = Pattern.compile(getString(R.string.pattern));
                        try {
                            String htmlDecode = URLDecoder.decode(url, "UTF-8");
                            Matcher matcher = pattern.matcher(htmlDecode.substring(12));
                            if (matcher.matches()) {
                                double left = Double.parseDouble(matcher.group(1));
                                double top = Double.parseDouble(matcher.group(2));
                                double width = Double.parseDouble(matcher.group(3));
                                double height = Double.parseDouble(matcher.group(4));
                                onHighlight((int) (UiUtil.convertDpToPixel((float) left,
                                        getActivity())),
                                        (int) (UiUtil.convertDpToPixel((float) top,
                                                getActivity())),
                                        (int) (UiUtil.convertDpToPixel((float) width,
                                                getActivity())),
                                        (int) (UiUtil.convertDpToPixel((float) height,
                                                getActivity())));
                            }
                        } catch (UnsupportedEncodingException e) {
                            Log.d(TAG, e.getMessage());
                        }
                    } else {
                        if (url.contains("storage")) {
                            mActivityCallback.setPagerToPosition(url);
                        } else {
                            // Otherwise, give the default behavior (open in browser)
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(intent);
                        }
                    }
                }
                return true;
            }
        });

        mWebview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {

                if (view.getProgress() == 100) {
                    mWebview.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("scroll y", "Scrolly" + mScrollY);
                            mWebview.scrollTo(0, mScrollY);
                        }
                    }, 100);
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (FolioPageFragment.this.isVisible()) {
                    if (TextUtils.isDigitsOnly(message)) {
                        mTotalMinutes = Integer.parseInt(message);
                    } else {
                        final Pattern pattern = Pattern.compile(getString(R.string.pattern));
                        Matcher matcher = pattern.matcher(message);
                        if (matcher.matches()) {
                            double left = Double.parseDouble(matcher.group(1));
                            double top = Double.parseDouble(matcher.group(2));
                            double width = Double.parseDouble(matcher.group(3));
                            double height = Double.parseDouble(matcher.group(4));
                            showTextSelectionMenu((int) (UiUtil.convertDpToPixel((float) left,
                                    getActivity())),
                                    (int) (UiUtil.convertDpToPixel((float) top,
                                            getActivity())),
                                    (int) (UiUtil.convertDpToPixel((float) width,
                                            getActivity())),
                                    (int) (UiUtil.convertDpToPixel((float) height,
                                            getActivity())));
                        } else {
                            if (mIsSpeaking && (!message.equals("undefined"))) {
                                if (isCurrentFragment()) {
                                    Log.d("FolioPageFragment", "Message from js: " + message);
                                    //speakAudio();
                                }
                            }
                        }
                    }
                    result.confirm();
                }
                return true;
            }
        });

        mTextSelectionSupport = TextSelectionSupport.support(getActivity(), mWebview);
        mTextSelectionSupport.setSelectionListener(new TextSelectionSupport.SelectionListener() {
            @Override
            public void startSelection() {
            }

            @Override
            public void selectionChanged(String text) {
                mSelectedText = text;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mWebview.loadUrl("javascript:alert(getRectForSelectedText())");
                    }
                });
            }

            @Override
            public void endSelection() {

            }
        });

        mWebview.getSettings().setDefaultTextEncodingName("utf-8");
        new HtmlTask(this).execute(getWebviewUrl());
    }

    private void initSeekbar() {
        mScrollSeekbar = (VerticalSeekbar) mRootView.findViewById(R.id.scrollSeekbar);
        mScrollSeekbar.getProgressDrawable()
                .setColorFilter(getResources()
                                .getColor(R.color.app_green),
                        PorterDuff.Mode.SRC_IN);
    }

    private void updatePagesLeftTextBg() {
        if (Config.getConfig().isNightMode()) {
            mRootView.findViewById(R.id.indicatorLayout)
                    .setBackgroundColor(Color.parseColor("#131313"));
        } else {
            mRootView.findViewById(R.id.indicatorLayout)
                    .setBackgroundColor(Color.WHITE);
        }
    }

    private void updatePagesLeftText(int scrollY) {
        try {
            int currentPage = (int) (Math.ceil((double) scrollY / mWebview.getWebviewHeight()) + 1);
            int totalPages =
                    (int) Math.ceil((double) mWebview.getContentHeightVal()
                            / mWebview.getWebviewHeight());
            int pagesRemaining = totalPages - currentPage;
            String pagesRemainingStrFormat =
                    pagesRemaining > 1 ?
                            getString(R.string.pages_left) : getString(R.string.page_left);
            String pagesRemainingStr = String.format(Locale.US,
                    pagesRemainingStrFormat, pagesRemaining);

            int minutesRemaining =
                    (int) Math.ceil((double) (pagesRemaining * mTotalMinutes) / totalPages);
            String minutesRemainingStr;
            if (minutesRemaining > 1) {
                minutesRemainingStr =
                        String.format(Locale.US, getString(R.string.minutes_left),
                                minutesRemaining);
            } else if (minutesRemaining == 1) {
                minutesRemainingStr =
                        String.format(Locale.US, getString(R.string.minute_left),
                                minutesRemaining);
            } else {
                minutesRemainingStr = getString(R.string.less_than_minute);
            }

            mMinutesLeftTextView.setText(minutesRemainingStr);
            mPagesLeftTextView.setText(pagesRemainingStr);
        } catch (java.lang.ArithmeticException exp) {
            Log.d("divide error", exp.toString());
        }
    }

    private void initAnimations() {
        mFadeInAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.fadein);
        mFadeInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mScrollSeekbar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mFadeOutAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.fadeout);
        mFadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mScrollSeekbar.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private Runnable mHideSeekbarRunnable = new Runnable() {
        @Override
        public void run() {
            fadeoutSeekbarIfVisible();
        }
    };

    public void fadeInSeekbarIfInvisible() {
        if (mScrollSeekbar.getVisibility() == View.INVISIBLE ||
                mScrollSeekbar.getVisibility() == View.GONE) {
            mScrollSeekbar.startAnimation(mFadeInAnimation);
        }
    }

    private void fadeoutSeekbarIfVisible() {
        if (mScrollSeekbar.getVisibility() == View.VISIBLE) {
            mScrollSeekbar.startAnimation(mFadeOutAnimation);
        }
    }

    @Override
    public void onDestroyView() {
        mFadeInAnimation.setAnimationListener(null);
        mFadeOutAnimation.setAnimationListener(null);
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FRAGMENT_FOLIO_POSITION, mPosition);
        outState.putString(KEY_FRAGMENT_FOLIO_BOOK_TITLE, mBookTitle);
        outState.putString(KEY_FRAGMENT_EPUB_FILE_NAME, mEpubFileName);
        outState.putSerializable(SPINE_ITEM, spineItem);
    }

    public void getTextSentence(Boolean isSpeaking) {
        if (isCurrentFragment()) {
            mIsSpeaking = true;
            mWebview.loadUrl("javascript:alert(getSentenceWithIndex('epub-media-overlay-playing'))");
        }
    }

    @Subscribe
    public void setStyle(String style) {
        if (isAdded()) {
            mWebview.loadUrl(String.format(getString(R.string.setmediaoverlaystyle), style));
        }
    }

    public String getSelectedText() {
        return mSelectedText;
    }

    public void highlight(Highlight.HighlightStyle style, boolean isCreated) {
        if (isCreated) {
            mWebview.loadUrl(String.format(getString(R.string.getHighlightString),
                    Highlight.HighlightStyle.classForStyle(style)));
        } else {
            mWebview.loadUrl(String.format(getString(R.string.sethighlightstyle),
                    Highlight.HighlightStyle.classForStyle(style)));
        }
    }

    public void highlightRemove() {
        mWebview.loadUrl("javascript:alert(removeThisHighlight())");
    }

    public void showTextSelectionMenu(int x, int y, final int width, final int height) {
        final ViewGroup root =
                (ViewGroup) getActivity().getWindow()
                        .getDecorView().findViewById(android.R.id.content);
        final View view = new View(getActivity());
        view.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        view.setBackgroundColor(Color.TRANSPARENT);

        root.addView(view);

        view.setX(x);
        view.setY(y);
        final QuickAction quickAction =
                new QuickAction(getActivity(), QuickAction.HORIZONTAL);
        quickAction.addActionItem(new ActionItem(ACTION_ID_COPY,
                getString(R.string.copy)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT,
                getString(R.string.highlight)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_DEFINE,
                getString(R.string.define)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_SHARE,
                getString(R.string.share)));
        quickAction.setOnActionItemClickListener(new QuickAction.OnActionItemClickListener() {
            @Override
            public void onItemClick(QuickAction source, int pos, int actionId) {
                quickAction.dismiss();
                root.removeView(view);
                onTextSelectionActionItemClicked(actionId, view, width, height);
            }
        });
        quickAction.show(view, width, height);
    }

    private void onTextSelectionActionItemClicked(int actionId, View view, int width, int height) {
        if (actionId == ACTION_ID_COPY) {
            UiUtil.copyToClipboard(getActivity(), mSelectedText);
            Toast.makeText(getActivity(), getString(R.string.copied), Toast.LENGTH_SHORT).show();
        } else if (actionId == ACTION_ID_SHARE) {
            UiUtil.share(getActivity(), mSelectedText);
        } else if (actionId == ACTION_ID_DEFINE) {
            //TODO: Check how to use define
        } else if (actionId == ACTION_ID_HIGHLIGHT) {
            onHighlight(view, width, height, true);
        }
    }

    private void onHighlight(int x, int y, int width, int height) {
        final View view = new View(getActivity());
        view.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setX(x);
        view.setY(y);
        onHighlight(view, width, height, false);
    }

    private void onHighlight(final View view, int width, int height, final boolean isCreated) {
        ViewGroup root =
                (ViewGroup) getActivity().getWindow().
                        getDecorView().findViewById(android.R.id.content);
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null) {
            root.addView(view);
        } else {
            final int index = parent.indexOfChild(view);
            parent.removeView(view);
            parent.addView(view, index);
        }

        final QuickAction quickAction = new QuickAction(getActivity(), QuickAction.HORIZONTAL);
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT_COLOR,
                getResources().getDrawable(R.drawable.colors_marker)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_DELETE,
                getResources().getDrawable(R.drawable.ic_action_discard)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_SHARE,
                getResources().getDrawable(R.drawable.ic_action_share)));
        final ViewGroup finalRoot = root;
        quickAction.setOnActionItemClickListener(new QuickAction.OnActionItemClickListener() {
            @Override
            public void onItemClick(QuickAction source, int pos, int actionId) {
                quickAction.dismiss();
                finalRoot.removeView(view);
                onHighlightActionItemClicked(actionId, view, isCreated);
            }
        });
        quickAction.show(view, width, height);
    }

    private void onHighlightActionItemClicked(int actionId, View view, boolean isCreated) {
        if (actionId == ACTION_ID_HIGHLIGHT_COLOR) {
            onHighlightColors(view, isCreated);
        } else if (actionId == ACTION_ID_SHARE) {
            UiUtil.share(getActivity(), mSelectedText);
        } else if (actionId == ACTION_ID_DELETE) {
            highlightRemove();
        }
    }

    private void onHighlightColors(final View view, final boolean isCreated) {
        ViewGroup root =
                (ViewGroup) getActivity().getWindow()
                        .getDecorView().findViewById(android.R.id.content);
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null) {
            root.addView(view);
        } else {
            final int index = parent.indexOfChild(view);
            parent.removeView(view);
            parent.addView(view, index);
        }

        final QuickAction quickAction = new QuickAction(getActivity(), QuickAction.HORIZONTAL);
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT_YELLOW,
                getResources().getDrawable(R.drawable.ic_yellow_marker)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT_GREEN,
                getResources().getDrawable(R.drawable.ic_green_marker)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT_BLUE,
                getResources().getDrawable(R.drawable.ic_blue_marker)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT_PINK,
                getResources().getDrawable(R.drawable.ic_pink_marker)));
        quickAction.addActionItem(new ActionItem(ACTION_ID_HIGHLIGHT_UNDERLINE,
                getResources().getDrawable(R.drawable.ic_underline_marker)));
        final ViewGroup finalRoot = root;
        quickAction.setOnActionItemClickListener(new QuickAction.OnActionItemClickListener() {
            @Override
            public void onItemClick(QuickAction source, int pos, int actionId) {
                quickAction.dismiss();
                finalRoot.removeView(view);
                onHighlightColorsActionItemClicked(actionId, view, isCreated);
            }
        });
        quickAction.show(view);
    }

    private void onHighlightColorsActionItemClicked(int actionId, View view, boolean isCreated) {
        if (actionId == ACTION_ID_HIGHLIGHT_YELLOW) {
            highlight(Highlight.HighlightStyle.Yellow, isCreated);
        } else if (actionId == ACTION_ID_HIGHLIGHT_GREEN) {
            highlight(Highlight.HighlightStyle.Green, isCreated);
        } else if (actionId == ACTION_ID_HIGHLIGHT_BLUE) {
            highlight(Highlight.HighlightStyle.Blue, isCreated);
        } else if (actionId == ACTION_ID_HIGHLIGHT_PINK) {
            highlight(Highlight.HighlightStyle.Pink, isCreated);
        } else if (actionId == ACTION_ID_HIGHLIGHT_UNDERLINE) {
            highlight(Highlight.HighlightStyle.Underline, isCreated);
        }
    }

    @JavascriptInterface
    public void getHighlightJson(String mJsonResponse) {
        if (mJsonResponse != null) {
            mHighlightMap = AppUtil.stringToJsonMap(mJsonResponse);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mWebview.loadUrl("javascript:alert(getHTML())");
                }
            });
        }
    }

    @JavascriptInterface
    public void getHtmlAndSaveHighlight(String html) {
        if (html != null && mHighlightMap != null) {
            Highlight highlight =
                    HighlightUtil.matchHighlight(html, mHighlightMap.get("id"), mBookTitle, mPosition);
            highlight.setCurrentWebviewScrollPos(mWebview.getScrollY());
            highlight = ((FolioActivity) getActivity()).setCurrentPagerPostion(highlight);
            HighLightTable.insertHighlight(highlight);
        }
    }

    @Subscribe
    public void setWebView(final WebViewPosition position) {
        if (position.getHref().equals(spineItem.href)) {
            setWebViewPosition(position.getWebviewPos());
        }
    }

    public void setWebViewPosition(final int position) {
        mWebview.post(new Runnable() {
            @Override
            public void run() {
                mWebview.scrollTo(0, position);
            }
        });
    }

    @JavascriptInterface
    public void getRemovedHighlightId(String id) {
        if (id != null) {
            HighLightTable.deleteHighlight(id);
        }
    }

    @JavascriptInterface
    public void getUpdatedHighlightId(String id, String style) {
        if (id != null) {
            HighLightTable.updateHighlightStyle(id, style);
        }
    }

    @Subscribe
    public void resetCurrentIndex(RewindIndexEvent resetIndex) {
        if (isCurrentFragment()) {
            mWebview.loadUrl("javascript:alert(rewindCurrentIndex())");
        }
    }

    private boolean isCurrentFragment() {
        return isAdded() && ((FolioActivity) getActivity()).getmChapterPosition() == mPos;
    }

    public void setFragmentPos(int pos) {
        mPos = pos;
    }

    @Override
    public void onError() {
    }
}