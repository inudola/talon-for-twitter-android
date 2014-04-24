package com.klinker.android.twitter.widget.launcher_fragment.adapters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.klinker.android.launcher.api.ResourceHelper;
import com.klinker.android.twitter.R;
import com.klinker.android.twitter.adapters.TimeLineCursorAdapter;
import com.klinker.android.twitter.data.App;
import com.klinker.android.twitter.data.sq_lite.DMDataSource;
import com.klinker.android.twitter.data.sq_lite.HomeSQLiteHelper;
import com.klinker.android.twitter.manipulations.PhotoViewerDialog;
import com.klinker.android.twitter.manipulations.widgets.NetworkedCacheableImageView;
import com.klinker.android.twitter.settings.AppSettings;
import com.klinker.android.twitter.ui.BrowserActivity;
import com.klinker.android.twitter.ui.compose.ComposeActivity;
import com.klinker.android.twitter.ui.profile_viewer.ProfilePager;
import com.klinker.android.twitter.ui.tweet_viewer.TweetPager;
import com.klinker.android.twitter.utils.EmojiUtils;
import com.klinker.android.twitter.utils.SDK11;
import com.klinker.android.twitter.utils.TweetLinkUtils;
import com.klinker.android.twitter.utils.Utils;
import com.klinker.android.twitter.utils.text.TextUtils;
import com.klinker.android.twitter.utils.text.TouchableMovementMethod;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.RejectedExecutionException;

import twitter4j.DirectMessage;
import twitter4j.MediaEntity;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import uk.co.senab.bitmapcache.BitmapLruCache;
import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

public class LauncherTimelineCursorAdapter extends CursorAdapter {

    private ResourceHelper helper;

    public Cursor cursor;
    public AppSettings settings;
    public Context context;
    public Context launcherContext;
    public final LayoutInflater inflater;
    private boolean isDM = false;
    private SharedPreferences sharedPrefs;

    private Handler[] mHandlers;
    private int currHandler;

    public boolean hasKeyboard = false;

    public int layout;
    public Resources res;
    private int talonLayout;
    private BitmapLruCache mCache;

    public java.text.DateFormat dateFormatter;
    public java.text.DateFormat timeFormatter;

    public boolean isHomeTimeline;

    public static class ViewHolder {
        public TextView name;
        public TextView screenTV;
        public ImageView profilePic;
        public TextView tweet;
        public TextView time;
        public TextView retweeter;
        public EditText reply;
        public ImageButton favorite;
        public ImageButton retweet;
        public TextView favCount;
        public TextView retweetCount;
        public LinearLayout expandArea;
        public ImageButton replyButton;
        public ImageView image;
        public LinearLayout background;
        public TextView charRemaining;
        public ImageView playButton;
        public ImageButton quoteButton;
        public ImageButton shareButton;
        //public Bitmap tweetPic;

        public long tweetId;
        public boolean isFavorited;
        public String screenName;
        public String picUrl;
        public String retweeterName;

        public boolean preventNextClick = false;

    }

    public LauncherTimelineCursorAdapter(Context talonContext, Context launcherContext, Cursor cursor, boolean isDM, boolean isHomeTimeline) {
        super(talonContext, cursor, 0);

        this.isHomeTimeline = isHomeTimeline;

        this.cursor = cursor;
        this.context = talonContext;
        this.launcherContext = launcherContext;
        this.inflater = LayoutInflater.from(context);
        this.isDM = isDM;

        helper = new ResourceHelper(context, "com.klinker.android.twitter");

        sharedPrefs = context.getSharedPreferences("com.klinker.android.twitter_world_preferences",
                Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE);

        settings = new AppSettings(sharedPrefs, context);

        talonLayout = settings.layout;

        if (settings.addonTheme) {
            try {
                res = context.getPackageManager().getResourcesForApplication(settings.addonThemePackage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        switch (talonLayout) {
            case AppSettings.LAYOUT_TALON:
                layout = R.layout.tweet;
                break;
            case AppSettings.LAYOUT_HANGOUT:
                layout = R.layout.tweet_hangout;
                break;
            case AppSettings.LAYOUT_FULL_SCREEN:
                layout = R.layout.tweet_full_screen;
                break;
        }

        mCache = getCache();

        dateFormatter = android.text.format.DateFormat.getDateFormat(context);
        timeFormatter = android.text.format.DateFormat.getTimeFormat(context);
        if (settings.militaryTime) {
            timeFormatter = new SimpleDateFormat("kk:mm");
        }


        mHandlers = new Handler[10];
        for (int i = 0; i < 10; i++) {
            mHandlers[i] = new Handler();
        }
    }

    public BitmapLruCache getCache() {
        try {
            File cacheDir = new File(context.getCacheDir(), "talon");
            cacheDir.mkdirs();

            BitmapLruCache.Builder builder = new BitmapLruCache.Builder();
            builder.setMemoryCacheEnabled(true).setMemoryCacheMaxSizeUsingHeapSize();
            builder.setDiskCacheEnabled(true).setDiskCacheLocation(cacheDir);

            return builder.build();
        } catch (Exception e) {
            return App.getInstance(context).getBitmapCache();
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        View v = null;
        final ViewHolder holder = new ViewHolder();
        if (settings.addonTheme) {
            try {
                Context viewContext = null;

                if (res == null) {
                    res = context.getPackageManager().getResourcesForApplication(settings.addonThemePackage);
                }

                try {
                    viewContext = context.createPackageContext(settings.addonThemePackage, Context.CONTEXT_IGNORE_SECURITY);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (res != null && viewContext != null) {
                    int id = res.getIdentifier("tweet", "layout", settings.addonThemePackage);
                    v = LayoutInflater.from(viewContext).inflate(res.getLayout(id), null);

                    holder.name = (TextView) v.findViewById(res.getIdentifier("name", "id", settings.addonThemePackage));
                    holder.screenTV = (TextView) v.findViewById(res.getIdentifier("screenname", "id", settings.addonThemePackage));
                    holder.profilePic = (ImageView) v.findViewById(res.getIdentifier("profile_pic", "id", settings.addonThemePackage));
                    holder.time = (TextView) v.findViewById(res.getIdentifier("time", "id", settings.addonThemePackage));
                    holder.tweet = (TextView) v.findViewById(res.getIdentifier("tweet", "id", settings.addonThemePackage));
                    holder.reply = (EditText) v.findViewById(res.getIdentifier("reply", "id", settings.addonThemePackage));
                    holder.favorite = (ImageButton) v.findViewById(res.getIdentifier("favorite", "id", settings.addonThemePackage));
                    holder.retweet = (ImageButton) v.findViewById(res.getIdentifier("retweet", "id", settings.addonThemePackage));
                    holder.favCount = (TextView) v.findViewById(res.getIdentifier("fav_count", "id", settings.addonThemePackage));
                    holder.retweetCount = (TextView) v.findViewById(res.getIdentifier("retweet_count", "id", settings.addonThemePackage));
                    holder.expandArea = (LinearLayout) v.findViewById(res.getIdentifier("expansion", "id", settings.addonThemePackage));
                    holder.replyButton = (ImageButton) v.findViewById(res.getIdentifier("reply_button", "id", settings.addonThemePackage));
                    holder.image = (ImageView) v.findViewById(res.getIdentifier("image", "id", settings.addonThemePackage));
                    holder.retweeter = (TextView) v.findViewById(res.getIdentifier("retweeter", "id", settings.addonThemePackage));
                    holder.background = (LinearLayout) v.findViewById(res.getIdentifier("background", "id", settings.addonThemePackage));
                    holder.charRemaining = (TextView) v.findViewById(res.getIdentifier("char_remaining", "id", settings.addonThemePackage));
                    holder.playButton = (ImageView) v.findViewById(res.getIdentifier("play_button", "id", settings.addonThemePackage));
                    try {
                        holder.quoteButton = (ImageButton) v.findViewById(res.getIdentifier("quote_button", "id", settings.addonThemePackage));
                        holder.shareButton = (ImageButton) v.findViewById(res.getIdentifier("share_button", "id", settings.addonThemePackage));
                    } catch (Exception e) {
                        // they don't exist because the theme was made before they were added
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
                switch (settings.theme) {
                    case AppSettings.THEME_LIGHT:
                        v = helper.getLayout("launcher_frag_tweet_light");
                        break;
                    default:
                        v = helper.getLayout("launcher_frag_tweet_dark");
                        break;
                }

                holder.name = (TextView) v.findViewById(helper.getId("name"));
                holder.screenTV = (TextView) v.findViewById(helper.getId("screenname"));
                holder.profilePic = (ImageView) v.findViewById(helper.getId("profile_pic"));
                holder.time = (TextView) v.findViewById(helper.getId("time"));
                holder.tweet = (TextView) v.findViewById(helper.getId("tweet"));
                holder.reply = (EditText) v.findViewById(helper.getId("reply"));
                holder.favorite = (ImageButton) v.findViewById(helper.getId("favorite"));
                holder.retweet = (ImageButton) v.findViewById(helper.getId("retweet"));
                holder.favCount = (TextView) v.findViewById(helper.getId("fav_count"));
                holder.retweetCount = (TextView) v.findViewById(helper.getId("retweet_count"));
                holder.expandArea = (LinearLayout) v.findViewById(helper.getId("expansion"));
                holder.replyButton = (ImageButton) v.findViewById(helper.getId("reply_button"));
                holder.image = (ImageView) v.findViewById(helper.getId("image"));
                holder.retweeter = (TextView) v.findViewById(helper.getId("retweeter"));
                holder.background = (LinearLayout) v.findViewById(helper.getId("background"));
                holder.charRemaining = (TextView) v.findViewById(helper.getId("char_remaining"));
                holder.playButton = (ImageView) v.findViewById(helper.getId("play_button"));
                try {
                    holder.quoteButton = (ImageButton) v.findViewById(helper.getId("quote_button"));
                    holder.shareButton = (ImageButton) v.findViewById(helper.getId("share_button"));
                } catch (Exception x) {
                    // theme was made before they were added
                }

            }
        } else {
            switch (settings.theme) {
                case AppSettings.THEME_LIGHT:
                    v = helper.getLayout("launcher_frag_tweet_light");
                    break;
                default:
                    v = helper.getLayout("launcher_frag_tweet_dark");
                    break;
            }

            holder.name = (TextView) v.findViewById(helper.getId("name"));
            holder.screenTV = (TextView) v.findViewById(helper.getId("screenname"));
            holder.profilePic = (ImageView) v.findViewById(helper.getId("profile_pic"));
            holder.time = (TextView) v.findViewById(helper.getId("time"));
            holder.tweet = (TextView) v.findViewById(helper.getId("tweet"));
            holder.reply = (EditText) v.findViewById(helper.getId("reply"));
            holder.favorite = (ImageButton) v.findViewById(helper.getId("favorite"));
            holder.retweet = (ImageButton) v.findViewById(helper.getId("retweet"));
            holder.favCount = (TextView) v.findViewById(helper.getId("fav_count"));
            holder.retweetCount = (TextView) v.findViewById(helper.getId("retweet_count"));
            holder.expandArea = (LinearLayout) v.findViewById(helper.getId("expansion"));
            holder.replyButton = (ImageButton) v.findViewById(helper.getId("reply_button"));
            holder.image = (ImageView) v.findViewById(helper.getId("image"));
            holder.retweeter = (TextView) v.findViewById(helper.getId("retweeter"));
            holder.background = (LinearLayout) v.findViewById(helper.getId("background"));
            holder.charRemaining = (TextView) v.findViewById(helper.getId("char_remaining"));
            holder.playButton = (ImageView) v.findViewById(helper.getId("play_button"));
            try {
                holder.quoteButton = (ImageButton) v.findViewById(helper.getId("quote_button"));
                holder.shareButton = (ImageButton) v.findViewById(helper.getId("share_button"));
            } catch (Exception x) {
                // theme was made before they were added
            }
        }

        // sets up the font sizes
        holder.tweet.setTextSize(settings.textSize);
        holder.screenTV.setTextSize(settings.textSize - 2);
        holder.name.setTextSize(settings.textSize + 4);
        holder.time.setTextSize(settings.textSize - 3);
        holder.retweeter.setTextSize(settings.textSize - 3);
        holder.favCount.setTextSize(settings.textSize + 1);
        holder.retweetCount.setTextSize(settings.textSize + 1);
        holder.reply.setTextSize(settings.textSize);

        v.setTag(holder);

        return v;
    }

    @Override
    public void bindView(final View view, Context mContext, final Cursor cursor) {
        final ViewHolder holder = (ViewHolder) view.getTag();

        if (holder.expandArea.getVisibility() == View.VISIBLE) {
            removeExpansionNoAnimation(holder);
            holder.retweetCount.setText(" -");
            holder.favCount.setText(" -");
            holder.reply.setText("");
            holder.retweet.clearColorFilter();
            holder.favorite.clearColorFilter();
        }

        final long id = cursor.getLong(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TWEET_ID));
        holder.tweetId = id;
        final String profilePic = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_PRO_PIC));
        String tweetTexts = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TEXT));
        final String name = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_NAME));
        final String screenname = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_SCREEN_NAME));
        final String picUrl = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_PIC_URL));
        holder.picUrl = picUrl;
        final long longTime = cursor.getLong(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TIME));
        final String otherUrl = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_URL));
        final String users = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_USERS));
        final String hashtags = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_HASHTAGS));

        String retweeter;
        try {
            retweeter = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_RETWEETER));
        } catch (Exception e) {
            retweeter = "";
        }

        final String tweetText = tweetTexts;

        if(!settings.reverseClickActions) {
            final String fRetweeter = retweeter;
            if (!isDM) {
                View.OnLongClickListener click = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        String link;

                        boolean displayPic = !holder.picUrl.equals("") && !holder.picUrl.contains("youtube");
                        if (displayPic) {
                            link = holder.picUrl;
                        } else {
                            link = otherUrl.split("  ")[0];
                        }

                        final Intent viewTweet = new Intent("android.intent.action.MAIN");
                        viewTweet.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.ui.tweet_viewer.LauncherTweetPager"));
                        viewTweet.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        viewTweet.putExtra("name", name);
                        viewTweet.putExtra("screenname", screenname);
                        viewTweet.putExtra("time", longTime);
                        viewTweet.putExtra("tweet", tweetText);
                        viewTweet.putExtra("retweeter", fRetweeter);
                        viewTweet.putExtra("webpage", link);
                        viewTweet.putExtra("other_links", otherUrl);
                        viewTweet.putExtra("picture", displayPic);
                        viewTweet.putExtra("tweetid", holder.tweetId);
                        viewTweet.putExtra("proPic", profilePic);
                        viewTweet.putExtra("users", users);
                        viewTweet.putExtra("hashtags", hashtags);

                        if (isHomeTimeline) {
                            sharedPrefs.edit()
                                    .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                                    .commit();
                        }

                        context.startActivity(viewTweet);

                        return true;
                    }
                };
                holder.background.setOnLongClickListener(click);
                //holder.tweet.setOnLongClickListener(click);
            }

            if (!isDM) {
                View.OnClickListener click = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (holder.preventNextClick) {
                            holder.preventNextClick = false;
                            return;
                        }
                        if (holder.expandArea.getVisibility() == View.GONE) {
                            addExpansion(holder, screenname, users, otherUrl.split("  "), holder.picUrl, id);
                        } else {
                            removeExpansionWithAnimation(holder);
                            removeKeyboard(holder);
                        }
                    }
                };
                holder.background.setOnClickListener(click);
                //holder.tweet.setOnClickListener(click);
            }
        } else {
            final String fRetweeter = retweeter;
            if (!isDM) {
                View.OnClickListener click = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (holder.preventNextClick) {
                            holder.preventNextClick = false;
                            return;
                        }
                        String link = "";

                        boolean displayPic = !holder.picUrl.equals("") && !holder.picUrl.contains("youtube");
                        if (displayPic) {
                            link = holder.picUrl;
                        } else {
                            link = otherUrl.split("  ")[0];
                        }

                        final Intent viewTweet = new Intent("android.intent.action.MAIN");
                        viewTweet.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.ui.tweet_viewer.LauncherTweetPager"));
                        viewTweet.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        viewTweet.putExtra("name", name);
                        viewTweet.putExtra("screenname", screenname);
                        viewTweet.putExtra("time", longTime);
                        viewTweet.putExtra("tweet", tweetText);
                        viewTweet.putExtra("retweeter", fRetweeter);
                        viewTweet.putExtra("webpage", link);
                        viewTweet.putExtra("picture", displayPic);
                        viewTweet.putExtra("other_links", otherUrl);
                        viewTweet.putExtra("tweetid", holder.tweetId);
                        viewTweet.putExtra("proPic", profilePic);
                        viewTweet.putExtra("users", users);
                        viewTweet.putExtra("hashtags", hashtags);

                        if (isHomeTimeline) {
                            sharedPrefs.edit()
                                    .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                                    .commit();
                        }

                        context.startActivity(viewTweet);
                    }
                };
                holder.background.setOnClickListener(click);
                //holder.tweet.setOnClickListener(click);
            }

            if (!isDM) {
                View.OnLongClickListener click = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {

                        if (holder.expandArea.getVisibility() != View.VISIBLE) {
                            addExpansion(holder, screenname, users, otherUrl.split("  "), holder.picUrl, id);
                        } else {
                            removeExpansionWithAnimation(holder);
                            removeKeyboard(holder);
                        }

                        return true;
                    }
                };

                holder.background.setOnLongClickListener(click);
                //holder.tweet.setOnLongClickListener(click);
            }
        }

        holder.profilePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                final Intent viewProfile = new Intent("android.intent.action.MAIN");
                viewProfile.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.ui.profile_viewer.LauncherProfilePager"));
                viewProfile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                viewProfile.putExtra("name", name);
                viewProfile.putExtra("screenname", screenname);
                viewProfile.putExtra("proPic", profilePic);
                viewProfile.putExtra("tweetid", holder.tweetId);
                viewProfile.putExtra("retweet", holder.retweeter.getVisibility() == View.VISIBLE);
                viewProfile.putExtra("long_click", false);

                if (isHomeTimeline) {
                    sharedPrefs.edit()
                            .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                            .commit();
                }

                context.startActivity(viewProfile);
            }
        });

        holder.profilePic.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View view) {

                final Intent viewProfile = new Intent("android.intent.action.MAIN");
                viewProfile.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.ui.profile_viewer.LauncherProfilePager"));
                viewProfile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                viewProfile.putExtra("name", name);
                viewProfile.putExtra("screenname", screenname);
                viewProfile.putExtra("proPic", profilePic);
                viewProfile.putExtra("tweetid", holder.tweetId);
                viewProfile.putExtra("retweet", holder.retweeter.getVisibility() == View.VISIBLE);
                viewProfile.putExtra("long_click", true);

                if (isHomeTimeline) {
                    sharedPrefs.edit()
                            .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                            .commit();
                }

                context.startActivity(viewProfile);

                return false;
            }
        });

        if (holder.screenTV.getVisibility() == View.GONE) {
            holder.screenTV.setVisibility(View.VISIBLE);
        }

        holder.screenTV.setText("@" + screenname);
        holder.name.setText(name);

        if (!settings.absoluteDate) {
            holder.time.setText(Utils.getTimeAgo(longTime, context));
        } else {
            Date date = new Date(longTime);
            holder.time.setText(timeFormatter.format(date).replace("24:", "00:") + ", " + dateFormatter.format(date));
        }

        holder.tweet.setText(tweetText);

        boolean picture = false;

        if(settings.inlinePics && holder.picUrl != null) {
            if (holder.picUrl.equals("")) {
                if (holder.image.getVisibility() != View.GONE) {
                    holder.image.setVisibility(View.GONE);
                }

                if (holder.playButton.getVisibility() == View.VISIBLE) {
                    holder.playButton.setVisibility(View.GONE);
                }
            } else {
                if (holder.image.getVisibility() == View.GONE) {
                    holder.image.setVisibility(View.VISIBLE);
                }

                if (holder.picUrl.contains("youtube")) {
                    if (holder.playButton.getVisibility() == View.GONE) {
                        holder.playButton.setVisibility(View.VISIBLE);
                    }

                    final String fRetweeter = retweeter;

                    holder.image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            String link;

                            boolean displayPic = !holder.picUrl.equals("") && !holder.picUrl.contains("youtube");
                            if (displayPic) {
                                link = holder.picUrl;
                            } else {
                                link = otherUrl.split("  ")[0];
                            }

                            final Intent viewTweet = new Intent("android.intent.action.MAIN");
                            viewTweet.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.ui.tweet_viewer.LauncherTweetPager"));
                            viewTweet.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            viewTweet.putExtra("name", name);
                            viewTweet.putExtra("screenname", screenname);
                            viewTweet.putExtra("time", longTime);
                            viewTweet.putExtra("tweet", tweetText);
                            viewTweet.putExtra("retweeter", fRetweeter);
                            viewTweet.putExtra("webpage", link);
                            viewTweet.putExtra("other_links", otherUrl);
                            viewTweet.putExtra("picture", displayPic);
                            viewTweet.putExtra("tweetid", holder.tweetId);
                            viewTweet.putExtra("proPic", profilePic);
                            viewTweet.putExtra("users", users);
                            viewTweet.putExtra("hashtags", hashtags);
                            viewTweet.putExtra("clicked_youtube", true);

                            if (isHomeTimeline) {
                                sharedPrefs.edit()
                                        .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                                        .commit();
                            }

                            context.startActivity(viewTweet);
                        }
                    });

                    holder.image.setImageDrawable(null);

                    picture = true;


                } else {
                    if (holder.playButton.getVisibility() == View.VISIBLE) {
                        holder.playButton.setVisibility(View.GONE);
                    }

                    holder.image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {

                            if (isHomeTimeline) {
                                sharedPrefs.edit()
                                        .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                                        .commit();
                            }

                            final Intent picture = new Intent("android.intent.action.MAIN");
                            picture.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.manipulations.LauncherPhotoViewerDialog"));
                            picture.putExtra("from_launcher", true);
                            picture.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            context.startActivity(picture.putExtra("url", holder.picUrl));
                        }
                    });

                    holder.image.setImageDrawable(null);

                    picture = true;
                }
            }
        }


        if (retweeter.length() > 0 && !isDM) {
            String text = helper.getString("retweeter");
            //holder.retweeter.setText(settings.displayScreenName ? text + retweeter : text.substring(0, text.length() - 2) + " " + name);
            holder.retweeter.setText(text + retweeter);
            holder.retweeterName = retweeter;
            holder.retweeter.setVisibility(View.VISIBLE);
        } else if (holder.retweeter.getVisibility() == View.VISIBLE) {
            holder.retweeter.setVisibility(View.GONE);
        }

        if (picture) {
            CacheableBitmapDrawable wrapper = mCache.getFromMemoryCache(holder.picUrl);
            if (wrapper != null) {
                holder.image.setImageDrawable(wrapper);
                picture = false;
            }
        }

        final boolean hasPicture = picture;
        mHandlers[currHandler].removeCallbacksAndMessages(null);
        mHandlers[currHandler].postDelayed(new Runnable() {
            @Override
            public void run() {
                if (holder.tweetId == id) {
                    if (hasPicture) {
                        loadImage(context, holder, holder.picUrl, mCache, id);
                    }

                    if (settings.useEmoji && (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || EmojiUtils.ios)) {
                        String text = holder.tweet.getText().toString();
                        if (EmojiUtils.emojiPattern.matcher(text).find()) {
                            final Spannable span = EmojiUtils.getSmiledText(context, Html.fromHtml(tweetText));
                            holder.tweet.setText(span);
                        }
                    }

                    holder.tweet.setSoundEffectsEnabled(false);
                    holder.tweet.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (!TouchableMovementMethod.touched) {
                                Log.v("talon_clickable", "clicked in the cursor adapter");
                                // we need to manually set the background for click feedback because the spannable
                                // absorbs the click on the background
                                if (!holder.preventNextClick) {
                                    /*holder.background.getBackground().setState(new int[]{android.R.attr.state_pressed});
                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            holder.background.getBackground().setState(new int[]{android.R.attr.state_empty});
                                        }
                                    }, 25);*/
                                }

                                holder.background.performClick();
                            }
                        }
                    });

                    holder.tweet.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View view) {
                            if (!TouchableMovementMethod.touched) {
                                holder.background.performLongClick();
                                holder.preventNextClick = true;
                            }
                            return false;
                        }
                    });

                    if (holder.retweeter.getVisibility() == View.VISIBLE) {
                        holder.retweeter.setSoundEffectsEnabled(false);
                        holder.retweeter.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                if (!TouchableMovementMethod.touched) {
                                    if (!holder.preventNextClick) {
                                        /*holder.background.getBackground().setState(new int[]{android.R.attr.state_pressed});
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                holder.background.getBackground().setState(new int[]{android.R.attr.state_empty});
                                            }
                                        }, 25);*/
                                    }

                                    holder.background.performClick();
                                }
                            }
                        });

                        holder.retweeter.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View view) {
                                if (!TouchableMovementMethod.touched) {
                                    holder.background.performLongClick();
                                    holder.preventNextClick = true;
                                }
                                return false;
                            }
                        });
                    }

                    TextUtils.linkifyText(context, settings, holder.tweet, holder.background, true, otherUrl, false);
                    TextUtils.linkifyText(context, settings, holder.retweeter, holder.background, true, "", false);

                }
            }
        }, 400);
        currHandler++;

        if (currHandler == 10) {
            currHandler = 0;
        }



    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        try {
            if (!cursor.moveToPosition(cursor.getCount() - 1 - position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }
        } catch (Exception e) {
            ((Activity)launcherContext).recreate();
            return null;
        }

        View v;
        if (convertView == null) {

            v = newView(context, cursor, parent);

        } else {
            v = convertView;

            final ViewHolder holder = (ViewHolder) v.getTag();

            holder.profilePic.setImageDrawable(helper.getDrawable("square_border_dark"));
            if (holder.image.getVisibility() == View.VISIBLE) {
                holder.image.setVisibility(View.GONE);
            }
        }

        bindView(v, context, cursor);

        return v;
    }

    public void removeExpansionWithAnimation(ViewHolder holder) {
        //ExpansionAnimation expandAni = new ExpansionAnimation(holder.expandArea, 450);
        holder.expandArea.setVisibility(View.GONE);//startAnimation(expandAni);
    }

    public void removeExpansionNoAnimation(ViewHolder holder) {
        //ExpansionAnimation expandAni = new ExpansionAnimation(holder.expandArea, 10);
        holder.expandArea.setVisibility(View.GONE);//startAnimation(expandAni);
    }

    public void addExpansion(final ViewHolder holder, String screenname, String users, final String[] otherLinks, final String webpage, final long tweetId) {
        if (isDM) {
            holder.retweet.setVisibility(View.GONE);
            holder.retweetCount.setVisibility(View.GONE);
            holder.favCount.setVisibility(View.GONE);
            holder.favorite.setVisibility(View.GONE);
        } else {
            holder.retweet.setVisibility(View.VISIBLE);
            holder.retweetCount.setVisibility(View.VISIBLE);
            holder.favCount.setVisibility(View.VISIBLE);
            holder.favorite.setVisibility(View.VISIBLE);
        }

        try {
            holder.replyButton.setVisibility(View.GONE);
        } catch (Exception e) {

        }
        try {
            holder.charRemaining.setVisibility(View.GONE);
        } catch (Exception e) {

        }

        holder.screenName = screenname;


        // used to find the other names on a tweet... could be optimized i guess, but only run when button is pressed
        if (!isDM) {
            String text = holder.tweet.getText().toString();
            String extraNames = "";

            if (text.contains("@")) {
                for (String s : users.split("  ")) {
                    if (!s.equals(settings.myScreenName) && !extraNames.contains(s) && !s.equals(screenname)) {
                        extraNames += "@" + s + " ";
                    }
                }
            }

            try {
                if (holder.retweeter.getVisibility() == View.VISIBLE && !extraNames.contains(holder.retweeterName)) {
                    extraNames += "@" + holder.retweeterName + " ";
                }
            } catch (NullPointerException e) {

            }

            if (!screenname.equals(settings.myScreenName)) {
                holder.reply.setText("@" + screenname + " " + extraNames);
            } else {
                holder.reply.setText(extraNames);
            }
        }

        holder.reply.setSelection(holder.reply.getText().length());

        if (holder.favCount.getText().toString().length() <= 2) {
            holder.favCount.setText(" -");
            holder.retweetCount.setText(" -");
        }

        //ExpansionAnimation expandAni = new ExpansionAnimation(holder.expandArea, 450);
        holder.expandArea.setVisibility(View.VISIBLE);//startAnimation(expandAni);

        if (holder.favCount.getText().toString().equals(" -")) {
            getCounts(holder, tweetId);
        }

        holder.favorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new FavoriteStatus(holder, holder.tweetId).execute();
            }
        });

        holder.retweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new RetweetStatus(holder, holder.tweetId).execute();
            }
        });

        holder.retweet.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                new AlertDialog.Builder(context)
                        .setTitle(helper.getString("remove_retweet"))
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new RemoveRetweet(holder.tweetId).execute();
                            }
                        })
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create()
                        .show();
                return false;
            }

            class RemoveRetweet extends AsyncTask<String, Void, Boolean> {

                private long tweetId;

                public RemoveRetweet(long tweetId) {
                    this.tweetId = tweetId;
                }

                protected void onPreExecute() {
                    holder.retweet.clearColorFilter();

                    Toast.makeText(context, helper.getString("removing_retweet"), Toast.LENGTH_SHORT).show();
                }

                protected Boolean doInBackground(String... urls) {
                    try {
                        Twitter twitter =  Utils.getTwitter(context, settings);
                        ResponseList<twitter4j.Status> retweets = twitter.getRetweets(tweetId);
                        for (twitter4j.Status retweet : retweets) {
                            if(retweet.getUser().getId() == settings.myId)
                                twitter.destroyStatus(retweet.getId());
                        }
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                protected void onPostExecute(Boolean deleted) {
                    try {
                        if (deleted) {
                            Toast.makeText(context, helper.getString("success"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, helper.getString("error"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        // user has gone away from the window
                    }
                }
            }
        });

        holder.reply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeExpansionWithAnimation(holder);

                final Intent compose = new Intent("android.intent.action.MAIN");
                compose.setComponent(new ComponentName("com.klinker.android.twitter", "com.klinker.android.twitter.ui.compose.LauncherCompose"));
                compose.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                String string = holder.reply.getText().toString();
                try{
                    compose.putExtra("user", string.substring(0, string.length() - 1));
                } catch (Exception e) {

                }
                compose.putExtra("id", holder.tweetId);

                if (isHomeTimeline) {
                    sharedPrefs.edit()
                            .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                            .commit();
                }

                context.startActivity(compose);
            }
        });

        holder.reply.requestFocus();
        removeKeyboard(holder);

        // this isn't going to run anymore, but just in case i put it back i guess
        if (holder.replyButton != null) {
            holder.replyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new ReplyToStatus(holder, holder.tweetId).execute();
                }
            });

            holder.charRemaining.setText(140 - holder.reply.getText().length() + "");

            holder.reply.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean b) {
                    hasKeyboard = b;
                }
            });

            holder.reply.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    holder.charRemaining.setText(140 - holder.reply.getText().length() + "");
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

        }

        final String name = screenname;

        try {
            holder.shareButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    String text = holder.tweet.getText().toString();

                    text = restoreLinks(text);

                    text = "@" + name + ": " + text;

                    Log.v("talon_sharing", "text: " + text);

                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET|Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(Intent.EXTRA_TEXT, text);

                    if (isHomeTimeline) {
                        sharedPrefs.edit()
                                .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                                .commit();
                    }

                    context.startActivity(Intent.createChooser(intent, helper.getString("menu_share"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }

                public String restoreLinks(String text) {
                    String full = text;

                    String[] split = text.split("\\s");
                    String[] otherLink = new String[otherLinks.length];

                    for (int i = 0; i < otherLinks.length; i++) {
                        otherLink[i] = "" + otherLinks[i];
                    }


                    boolean changed = false;

                    if (otherLink.length > 0) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];

                            if (Patterns.WEB_URL.matcher(s).find()) { // we know the link is cut off
                                String f = s.replace("...", "").replace("http", "");

                                for (int x = 0; x < otherLink.length; x++) {
                                    if (otherLink[x].contains(f)) {
                                        changed = true;
                                        // for some reason it wouldn't match the last "/" on a url and it was stopping it from opening
                                        if (otherLink[x].substring(otherLink[x].length() - 1, otherLink[x].length()).equals("/")) {
                                            otherLink[x] = otherLink[x].substring(0, otherLink[x].length() - 1);
                                        }
                                        f = otherLink[x];
                                        otherLink[x] = "";
                                        break;
                                    }
                                }

                                if (changed) {
                                    split[i] = f;
                                } else {
                                    split[i] = s;
                                }
                            } else {
                                split[i] = s;
                            }

                        }
                    }

                    if (!webpage.equals("")) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];
                            s = s.replace("...", "");

                            if (Patterns.WEB_URL.matcher(s).find() && (s.startsWith("t.co/") || s.contains("twitter.com/"))) { // we know the link is cut off
                                String replace = otherLinks[otherLinks.length - 1];
                                if (replace.replace(" ", "").equals("")) {
                                    replace = webpage;
                                }
                                split[i] = replace;
                                changed = true;
                            }
                        }
                    }



                    if(changed) {
                        full = "";
                        for (String p : split) {
                            full += p + " ";
                        }

                        full = full.substring(0, full.length() - 1);
                    }

                    return full;
                }
            });


            holder.quoteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    final Intent intent = new Intent("android.intent.action.MAIN");
                    intent.setComponent(new ComponentName("com.klinker.android.twitter",
                            "com.klinker.android.twitter.ui.compose.LauncherCompose"));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    intent.setType("text/plain");
                    String text = holder.tweet.getText().toString();

                    text = TweetLinkUtils.removeColorHtml(text, settings);
                    text = restoreLinks(text);

                    if (!settings.preferRT) {
                        text = "\"@" + name + ": " + text + "\" ";
                    } else {
                        text = " RT @" + name + ": " + text;
                    }
                    intent.putExtra("user", text);
                    intent.putExtra("id", tweetId);

                    if (isHomeTimeline) {
                        sharedPrefs.edit()
                                .putLong("current_position_" + settings.currentAccount, holder.tweetId)
                                .commit();
                    }

                    context.startActivity(intent);
                }

                public String restoreLinks(String text) {
                    String full = text;

                    String[] split = text.split("\\s");
                    String[] otherLink = new String[otherLinks.length];

                    for (int i = 0; i < otherLinks.length; i++) {
                        otherLink[i] = "" + otherLinks[i];
                    }


                    boolean changed = false;

                    if (otherLink.length > 0) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];

                            if (Patterns.WEB_URL.matcher(s).find()) { // we know the link is cut off
                                String f = s.replace("...", "").replace("http", "");

                                for (int x = 0; x < otherLink.length; x++) {
                                    if (otherLink[x].contains(f)) {
                                        changed = true;
                                        // for some reason it wouldn't match the last "/" on a url and it was stopping it from opening
                                        if (otherLink[x].substring(otherLink[x].length() - 1, otherLink[x].length()).equals("/")) {
                                            otherLink[x] = otherLink[x].substring(0, otherLink[x].length() - 1);
                                        }
                                        f = otherLink[x];
                                        otherLink[x] = "";
                                        break;
                                    }
                                }

                                if (changed) {
                                    split[i] = f;
                                } else {
                                    split[i] = s;
                                }
                            } else {
                                split[i] = s;
                            }

                        }
                    }

                    if (!webpage.equals("")) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];
                            s = s.replace("...", "");

                            if (Patterns.WEB_URL.matcher(s).find() && (s.startsWith("t.co/") || s.contains("twitter.com/"))) { // we know the link is cut off
                                String replace = otherLinks[otherLinks.length - 1];
                                if (replace.replace(" ", "").equals("")) {
                                    replace = webpage;
                                }
                                split[i] = replace;
                                changed = true;
                            }
                        }
                    }



                    if(changed) {
                        full = "";
                        for (String p : split) {
                            full += p + " ";
                        }

                        full = full.substring(0, full.length() - 1);
                    }

                    return full;
                }
            });
        } catch (Exception e) {
            // theme made before these were implemented
        }
        if (settings.addonTheme) {
            try {
                Resources resourceAddon = context.getPackageManager().getResourcesForApplication(settings.addonThemePackage);
                int back = resourceAddon.getIdentifier("reply_entry_background", "drawable", settings.addonThemePackage);
                holder.reply.setBackgroundDrawable(resourceAddon.getDrawable(back));
            } catch (Exception e) {
                // theme does not include a reply entry box
            }
        }
    }

    public void removeKeyboard(ViewHolder holder) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(holder.reply.getWindowToken(), 0);
    }

    public void getFavoriteCount(final ViewHolder holder, final long tweetId) {

        Thread getCount = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter =  Utils.getTwitter(context, settings);
                    final Status status;
                    if (holder.retweeter.getVisibility() != View.GONE) {
                        status = twitter.showStatus(holder.tweetId).getRetweetedStatus();
                    } else {
                        status = twitter.showStatus(tweetId);
                    }

                    if (status != null && holder.tweetId == tweetId) {
                        ((Activity)launcherContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                holder.favCount.setText(" " + status.getFavoriteCount());

                                if (status.isFavorited()) {
                                    if (!settings.addonTheme) {
                                        holder.favorite.setColorFilter(helper.getColor("app_color"));
                                    } else {
                                        holder.favorite.setColorFilter(settings.accentInt);
                                    }

                                    holder.favorite.setImageDrawable(helper.getDrawable("ic_action_important_dark"));
                                    holder.isFavorited = true;
                                } else {
                                    holder.favorite.setImageDrawable(helper.getDrawable("ic_action_not_important_dark"));
                                    holder.isFavorited = false;

                                    holder.favorite.clearColorFilter();
                                }
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        getCount.setPriority(7);
        getCount.start();
    }

    public void getCounts(final ViewHolder holder, final long tweetId) {

        Thread getCount = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter =  Utils.getTwitter(context, settings);
                    final Status status;
                    if (holder.retweeter.getVisibility() != View.GONE) {
                        status = twitter.showStatus(holder.tweetId).getRetweetedStatus();
                    } else {
                        status = twitter.showStatus(tweetId);
                    }

                    if (status != null && holder.tweetId == tweetId) {
                        ((Activity)launcherContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                holder.favCount.setText(" " + status.getFavoriteCount());
                                holder.retweetCount.setText(" " + status.getRetweetCount());

                                if (status.isFavorited()) {

                                    if (!settings.addonTheme) {
                                        holder.favorite.setColorFilter(helper.getColor("app_color"));
                                    } else {
                                        holder.favorite.setColorFilter(settings.accentInt);
                                    }

                                    holder.favorite.setImageDrawable(helper.getDrawable("ic_action_important_dark"));
                                    holder.isFavorited = true;
                                } else {

                                    holder.favorite.setImageDrawable(helper.getDrawable("ic_action_not_important_dark"));
                                    holder.isFavorited = false;

                                    holder.favorite.clearColorFilter();
                                }

                                if (status.isRetweetedByMe()) {
                                    if (!settings.addonTheme) {
                                        holder.retweet.setColorFilter(helper.getColor("app_color"));
                                    } else {
                                        holder.retweet.setColorFilter(settings.accentInt);
                                    }
                                } else {
                                    holder.retweet.clearColorFilter();
                                }
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        getCount.setPriority(7);
        getCount.start();
    }

    public void getRetweetCount(final ViewHolder holder, final long tweetId) {

        Thread getRetweetCount = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter =  Utils.getTwitter(context, settings);
                    twitter4j.Status status = twitter.showStatus(holder.tweetId);
                    final boolean retweetedByMe = status.isRetweetedByMe();
                    final String count = "" + status.getRetweetCount();
                    ((Activity)launcherContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (tweetId == holder.tweetId) {
                                if (retweetedByMe) {
                                    if (!settings.addonTheme) {
                                        holder.retweet.setColorFilter(helper.getColor("app_color"));
                                    } else {
                                        holder.retweet.setColorFilter(settings.accentInt);
                                    }
                                } else {
                                    holder.retweet.clearColorFilter();
                                }
                                if (count != null) {
                                    holder.retweetCount.setText(" " + count);
                                }
                            }
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        getRetweetCount.setPriority(7);
        getRetweetCount.start();
    }

    class FavoriteStatus extends AsyncTask<String, Void, String> {

        private ViewHolder holder;
        private long tweetId;

        public FavoriteStatus(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected void onPreExecute() {
            if (!holder.isFavorited) {
                Toast.makeText(context, helper.getString("favoriting_status"), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, helper.getString("removing_favorite"), Toast.LENGTH_SHORT).show();
            }
        }

        protected String doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);
                if (holder.isFavorited) {
                    twitter.destroyFavorite(tweetId);
                } else {
                    twitter.createFavorite(tweetId);
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(String count) {
            Toast.makeText(context, helper.getString("success"), Toast.LENGTH_SHORT).show();
            getFavoriteCount(holder, tweetId);
        }
    }

    class RetweetStatus extends AsyncTask<String, Void, String> {

        private ViewHolder holder;
        private long tweetId;

        public RetweetStatus(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected void onPreExecute() {
            Toast.makeText(context, helper.getString("retweeting_status"), Toast.LENGTH_SHORT).show();
        }

        protected String doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);
                twitter.retweetStatus(tweetId);
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(String count) {
            Toast.makeText(context, helper.getString("retweet_success"), Toast.LENGTH_SHORT).show();
            getRetweetCount(holder, tweetId);
        }
    }

    class ReplyToStatus extends AsyncTask<String, Void, Boolean> {

        private ViewHolder holder;
        private long tweetId;
        private boolean dontgo = false;

        public ReplyToStatus(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected void onPreExecute() {
            if (Integer.parseInt(holder.charRemaining.getText().toString()) >= 0) {
                removeExpansionWithAnimation(holder);
                removeKeyboard(holder);
            } else {
                dontgo = true;
            }
        }

        protected Boolean doInBackground(String... urls) {
            try {
                if (!dontgo) {
                    Twitter twitter =  Utils.getTwitter(context, settings);

                    if (!isDM) {
                        twitter4j.StatusUpdate reply = new twitter4j.StatusUpdate(holder.reply.getText().toString());
                        reply.setInReplyToStatusId(tweetId);

                        twitter.updateStatus(reply);
                    } else {
                        String screenName = holder.screenName;
                        String message = holder.reply.getText().toString();
                        DirectMessage dm = twitter.sendDirectMessage(screenName, message);
                    }


                    return true;
                }
            } catch (Exception e) {

            }

            return false;
        }

        protected void onPostExecute(Boolean finished) {
            if (finished) {
                Toast.makeText(context, helper.getString("tweet_success"), Toast.LENGTH_SHORT).show();
            } else {
                if (dontgo) {
                    Toast.makeText(context, helper.getString("tweet_to_long"), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, helper.getString("error_sending_tweet"), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    class GetImage extends AsyncTask<String, Void, String> {

        private ViewHolder holder;
        private long tweetId;

        public GetImage(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected String doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);
                twitter4j.Status status = twitter.showStatus(tweetId);

                MediaEntity[] entities = status.getMediaEntities();



                return entities[0].getMediaURL();
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(String url) {

        }
    }

    // used to place images on the timeline
    public static ImageUrlAsyncTask mCurrentTask;

    public void loadImage(Context context, final ViewHolder holder, final String url, BitmapLruCache mCache, final long tweetId) {
        // First check whether there's already a task running, if so cancel it
        /*if (null != mCurrentTask) {
            mCurrentTask.cancel(true);
        }*/

        if (url == null) {
            return;
        }

        BitmapDrawable wrapper = mCache.getFromMemoryCache(url);

        if (null != wrapper && holder.image.getVisibility() != View.GONE) {
            // The cache has it, so just display it
            holder.image.setImageDrawable(wrapper);Animation fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in);

            holder.image.startAnimation(fadeInAnimation);
        } else {
            // Memory Cache doesn't have the URL, do threaded request...
            holder.image.setImageDrawable(null);

            mCurrentTask = new ImageUrlAsyncTask(context, holder, mCache, tweetId);

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    SDK11.executeOnThreadPool(mCurrentTask, url);
                } else {
                    mCurrentTask.execute(url);
                }
            } catch (RejectedExecutionException e) {
                // This shouldn't happen, but might.
            }

        }
    }

    private static class ImageUrlAsyncTask
            extends AsyncTask<String, Void, CacheableBitmapDrawable> {

        private BitmapLruCache mCache;
        private Context context;
        private ViewHolder holder;
        private long id;

        ImageUrlAsyncTask(Context context, ViewHolder holder, BitmapLruCache cache, long tweetId) {
            this.context = context;
            mCache = cache;
            this.holder = holder;
            this.id = tweetId;
        }

        @Override
        protected CacheableBitmapDrawable doInBackground(String... params) {
            try {
                if (holder.tweetId != id) {
                    return null;
                }
                final String url = params[0];

                // Now we're not on the main thread we can check all caches
                CacheableBitmapDrawable result;

                result = mCache.get(url, null);

                if (null == result) {

                    // The bitmap isn't cached so download from the web
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    InputStream is = new BufferedInputStream(conn.getInputStream());

                    Bitmap b = decodeSampledBitmapFromResourceMemOpt(is, 500, 500);

                    try {
                        is.close();
                    } catch (Exception e) {

                    }
                    try {
                        conn.disconnect();
                    } catch (Exception e) {

                    }

                    // Add to cache
                    if (b != null) {
                        result = mCache.put(url, b);
                    }

                }

                return result;

            } catch (IOException e) {
                Log.e("ImageUrlAsyncTask", e.toString());
            } catch (OutOfMemoryError e) {
                Log.v("ImageUrlAsyncTask", "Out of memory error here");
            }

            return null;
        }

        public Bitmap decodeSampledBitmapFromResourceMemOpt(
                InputStream inputStream, int reqWidth, int reqHeight) {

            byte[] byteArr = new byte[0];
            byte[] buffer = new byte[1024];
            int len;
            int count = 0;

            try {
                while ((len = inputStream.read(buffer)) > -1) {
                    if (len != 0) {
                        if (count + len > byteArr.length) {
                            byte[] newbuf = new byte[(count + len) * 2];
                            System.arraycopy(byteArr, 0, newbuf, 0, count);
                            byteArr = newbuf;
                        }

                        System.arraycopy(buffer, 0, byteArr, count, len);
                        count += len;
                    }
                }

                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(byteArr, 0, count, options);

                options.inSampleSize = calculateInSampleSize(options, reqWidth,
                        reqHeight);
                options.inPurgeable = true;
                options.inInputShareable = true;
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                return BitmapFactory.decodeByteArray(byteArr, 0, count, options);

            } catch (Exception e) {
                e.printStackTrace();

                return null;
            }
        }

        public static int calculateInSampleSize(BitmapFactory.Options opt, int reqWidth, int reqHeight) {
            // Raw height and width of image
            final int height = opt.outHeight;
            final int width = opt.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {

                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) > reqHeight
                        && (halfWidth / inSampleSize) > reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }

        @Override
        protected void onPostExecute(CacheableBitmapDrawable result) {
            super.onPostExecute(result);

            try {
                if (result != null && holder.tweetId == id) {
                    holder.image.setImageDrawable(result);
                    Animation fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in_fast);

                    if (holder.tweetId == id) {
                        holder.image.startAnimation(fadeInAnimation);
                    }
                }

            } catch (Exception e) {

            }
        }
    }
}