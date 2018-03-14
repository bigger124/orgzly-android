package com.orgzly.android.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.ActionService;
import com.orgzly.android.AppIntent;
import com.orgzly.android.Book;
import com.orgzly.android.BookName;
import com.orgzly.android.Note;
import com.orgzly.android.NotesBatch;
import com.orgzly.android.Notifications;
import com.orgzly.android.Shelf;
import com.orgzly.android.filter.Filter;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.provider.clients.BooksClient;
import com.orgzly.android.provider.clients.ReposClient;
import com.orgzly.android.query.Condition;
import com.orgzly.android.query.Query;
import com.orgzly.android.query.user.DottedQueryBuilder;
import com.orgzly.android.repos.Repo;
import com.orgzly.android.ui.dialogs.SimpleOneLinerDialog;
import com.orgzly.android.ui.drawer.DrawerNavigationView;
import com.orgzly.android.ui.fragments.BookFragment;
import com.orgzly.android.ui.fragments.BookPrefaceFragment;
import com.orgzly.android.ui.fragments.BooksFragment;
import com.orgzly.android.ui.fragments.FilterFragment;
import com.orgzly.android.ui.fragments.FiltersFragment;
import com.orgzly.android.ui.fragments.NoteFragment;
import com.orgzly.android.ui.fragments.NoteListFragment;
import com.orgzly.android.ui.fragments.SyncFragment;
import com.orgzly.android.ui.settings.SettingsActivity;
import com.orgzly.android.ui.util.ActivityUtils;
import com.orgzly.android.util.AppPermissions;
import com.orgzly.android.util.LogUtils;
import com.orgzly.android.util.MiscUtils;
import com.orgzly.org.datetime.OrgDateTime;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MainActivity extends CommonActivity
        implements
        ActionModeListener,
        FilterFragment.FilterFragmentListener,
        FiltersFragment.FiltersFragmentListener,
        BooksFragment.BooksFragmentListener,
        BookFragment.BookFragmentListener,
        NoteFragment.NoteFragmentListener,
        SyncFragment.SyncFragmentListener,
        SimpleOneLinerDialog.SimpleOneLinerDialogListener,
        BookPrefaceFragment.EditorListener,
        NoteListFragment.NoteListFragmentListener {

    public static final String TAG = MainActivity.class.getName();

    public static final int ACTIVITY_REQUEST_CODE_FOR_BOOK_IMPORT = 0;
    public static final int ACTIVITY_REQUEST_CODE_FOR_QUERIES_IMPORT = 1;

    private static final int DIALOG_NEW_BOOK = 1;
    private static final int DIALOG_IMPORT_BOOK = 2;

    public SyncFragment mSyncFragment;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerNavigationView drawerNavigationView;

    private LocalBroadcastManager broadcastManager;

    private CharSequence mSavedTitle;
    private CharSequence mSavedSubtitle;

    /**
     * Original title used when book is not being displayed.
     */
    private CharSequence mDefaultTitle;

    private ActionMode mActionMode;
    private boolean mPromoteDemoteOrMoveRequested = false;

    private Runnable runnableOnResumeFragments;

    private BroadcastReceiver receiver = new LocalBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);


        mSavedTitle = mDefaultTitle = getTitle();
        mSavedSubtitle = null;


        broadcastManager = LocalBroadcastManager.getInstance(this);

        setupDrawer();

        setupDisplay(savedInstanceState);

        if (AppPreferences.newNoteNotification(this)) {
            Notifications.createNewNoteNotification(this);
        }
    }

    /**
     * Adds initial set of fragments, depending on intent extras
     */
    private void setupDisplay(Bundle savedInstanceState) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, getIntent().getExtras());

        if (savedInstanceState == null) { // Not a configuration change.
            long bookId = getIntent().getLongExtra(AppIntent.EXTRA_BOOK_ID, 0L);
            long noteId = getIntent().getLongExtra(AppIntent.EXTRA_NOTE_ID, 0L);
            String queryString = getIntent().getStringExtra(AppIntent.EXTRA_QUERY_STRING);

            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, bookId, noteId, queryString);

            DisplayManager.displayBooks(getSupportFragmentManager(), false);

            /* Display requested book and note. */
            if (bookId > 0) {
                DisplayManager.displayBook(getSupportFragmentManager(), bookId, noteId);
                if (noteId > 0) {
                    DisplayManager.displayNote(getSupportFragmentManager(), bookId, noteId);
                }
            } else if (queryString != null) {
                DisplayManager.displayQuery(getSupportFragmentManager(), queryString);
            }
        }
    }

    private void setupDrawer() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        NavigationView navigationView = mDrawerLayout.findViewById(R.id.drawer_navigation_view);

        navigationView.setNavigationItemSelectedListener(item -> {
            Intent intent = item.getIntent();

            if (intent != null) {
                mDrawerLayout.closeDrawer(GravityCompat.START);

                // Avoid jerky drawer close by displaying new fragment with a delay
                new Handler().postDelayed(() -> broadcastManager.sendBroadcast(intent), 200);
            }

            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, item, intent);

            return true;
        });

        drawerNavigationView = new DrawerNavigationView(this, navigationView);

        if (mDrawerLayout != null) {
            // Set the drawer toggle as the DrawerListener
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
                private int state = -1;

                /*
                 * onDrawerOpened and onDrawerClosed are not called fast enough.
                 * So state is determined using onDrawerSlide callback and checking the slide offset.
                 */
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    super.onDrawerSlide(drawerView, slideOffset);

                    switch (state) {
                        case -1: // Unknown
                            if (slideOffset == 0) {
                                state = 0;
                                drawerClosed();

                            } else if (slideOffset > 0) {
                                state = 1;
                                drawerOpened();
                            }
                            break;

                        case 0: // Starting to open the drawer
                            if (slideOffset > 0) {
                                state = 1;
                                drawerOpened();
                            }
                            break;

                        case 1: // Starting to close the drawer
                            if (slideOffset == 0) {
                                state = 0;
                                drawerClosed();
                            }
                            break;
                    }
                }
            };

            // No flipping burgers
            mDrawerToggle.setDrawerSlideAnimationEnabled(false);

            mDrawerLayout.addDrawerListener(mDrawerToggle);
        }

        mSyncFragment = addSyncFragment();
    }

    private void drawerOpened() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        ActivityUtils.INSTANCE.closeSoftKeyboard(this);
    }

    private void drawerClosed() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);
    }

    private SyncFragment addSyncFragment() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        SyncFragment fragment = (SyncFragment) getSupportFragmentManager()
                .findFragmentByTag(SyncFragment.FRAGMENT_TAG);

        /* If the Fragment is non-null, then it is currently being
         * retained across a configuration change.
         */
        if (fragment == null) {
            fragment = SyncFragment.getInstance();

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.drawer_sync_container, fragment, SyncFragment.FRAGMENT_TAG)
                    .commit();
        }

        return fragment;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState);
        super.onPostCreate(savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, newConfig);
        super.onConfigurationChanged(newConfig);

        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onResume() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);
        super.onResume();

        if (clearFragmentBackstack) {
            DisplayManager.clear(getSupportFragmentManager());
            clearFragmentBackstack = false;
        }

        performIntros();

        Shelf shelf = new Shelf(this);
        shelf.syncOnResume();
    }

    private void performIntros() {
        int currentVersion = AppPreferences.lastUsedVersionCode(this);
        boolean isNewVersion = checkIfNewAndUpdateVersion();

        if (isNewVersion) {
            /* Import Getting Started notebook. */
            if (!AppPreferences.isGettingStartedNotebookLoaded(this)) {
                ActionService.Companion.enqueueWork(
                        MainActivity.this,
                        AppIntent.ACTION_IMPORT_GETTING_STARTED_NOTEBOOK);
            }

            /* Open drawer for the first time user. */
            if (currentVersion == 0 && mDrawerLayout != null) {
                mDrawerLayout.openDrawer(GravityCompat.START);
            }

            displayWhatsNewDialog();
        }
    }

    private boolean checkIfNewAndUpdateVersion() {
        boolean isNewVersion = false;


        if (BuildConfig.VERSION_CODE > AppPreferences.lastUsedVersionCode(this)) {
            isNewVersion = true;
        }

        AppPreferences.lastUsedVersionCode(this, BuildConfig.VERSION_CODE);

        return isNewVersion;
    }

    @Override
    protected void onPause() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);
        super.onPause();

        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);
        super.onDestroy();

        if (mDrawerLayout != null && mDrawerToggle != null) {
            mDrawerLayout.removeDrawerListener(mDrawerToggle);
        }
    }

    @Override
    public void onBackPressed() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        /* Close drawer if opened. */
        if (mDrawerLayout != null) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
                return;
            }
        }

        /* Handle back press when editing note - check for changes */
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(NoteFragment.FRAGMENT_TAG);
        if (fragment != null && fragment instanceof NoteFragment && fragment.isVisible()) {
            final NoteFragment noteFragment = (NoteFragment) fragment;
            if (noteFragment.isAskingForConfirmationForModifiedNote()) {
                return;
            }
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, fragment);

        super.onAttachFragment(fragment);
    }

    @Override
    protected void onResumeFragments() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);
        super.onResumeFragments();

        /* Showing dialog in onResume() fails with:
         *   Can not perform this action after onSaveInstanceState
         */
        if (runnableOnResumeFragments != null) {
            runnableOnResumeFragments.run();
            runnableOnResumeFragments = null;
        }

        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(receiver, new IntentFilter(AppIntent.ACTION_OPEN_NOTE));
        bm.registerReceiver(receiver, new IntentFilter(AppIntent.ACTION_OPEN_QUERIES));
        bm.registerReceiver(receiver, new IntentFilter(AppIntent.ACTION_OPEN_QUERY));
        bm.registerReceiver(receiver, new IntentFilter(AppIntent.ACTION_OPEN_BOOKS));
        bm.registerReceiver(receiver, new IntentFilter(AppIntent.ACTION_OPEN_BOOK));
        bm.registerReceiver(receiver, new IntentFilter(AppIntent.ACTION_OPEN_SETTINGS));
    }

    /**
     * Callback for options menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, menu);
        super.onCreateOptionsMenu(menu);

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_actions, menu);

        setupSearchView(menu);

//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                tryDisplayingTooltip();
//            }
//        }, 200);

        return true;
    }

    /**
     * SearchView setup and query text listeners.
     * TODO: http://developer.android.com/training/search/setup.html
     */
    private void setupSearchView(Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.activity_action_search);

        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint(getString(R.string.search_hint));

        /* When user starts the search, fill the search box with text depending on current fragment. */
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Make search as wide as possible. */
                ViewGroup.LayoutParams layoutParams = searchView.getLayoutParams();
                layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;

                /* For Query fragment, fill the box with full query. */
                String q = DisplayManager.getDisplayedQuery(getSupportFragmentManager());
                if (q != null) {
                    searchView.setQuery(q + " ", false);

                } else {
                    /* If searching from book, add book name to query. */
                    Book book = getActiveFragmentBook();
                    if (book != null) {
                        DottedQueryBuilder builder = new DottedQueryBuilder();
                        String query = builder.build(new Query(new Condition.InBook(book.getName())));
                        searchView.setQuery(query + " ", false);
                    }
                }
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String str) {
                return false;
            }

            @Override
            public boolean onQueryTextSubmit(String str) {
                if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, str);

                /* Close search. */
                searchItem.collapseActionView();

                DisplayManager.displayQuery(getSupportFragmentManager(), str.trim());

                return true;
            }
        });
    }

    /**
     * Callback for options menu.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.activity_action_sync:
                mSyncFragment.onSyncButton();
                return true;

            case R.id.activity_action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTIVITY_REQUEST_CODE_FOR_BOOK_IMPORT:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    runnableOnResumeFragments = () -> importChosenBook(uri);
                }
                break;

            case ACTIVITY_REQUEST_CODE_FOR_QUERIES_IMPORT:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    runnableOnResumeFragments = () -> mSyncFragment.importQueries(uri);
                }
                break;
        }
    }

    /**
     * Display a dialog for user to enter notebook's name.
     */
    private void importChosenBook(Uri uri) {
        String guessedBookName = guessBookNameFromUri(uri);

        Bundle bundle = new Bundle();
        bundle.putString("uri", uri.toString());

        SimpleOneLinerDialog
                .getInstance(DIALOG_IMPORT_BOOK, R.string.import_as, R.string.name, R.string.import_, R.string.cancel, guessedBookName, bundle)
                .show(getSupportFragmentManager(), SimpleOneLinerDialog.FRAGMENT_TAG);
    }

    /**
     * @return Guessed book name or {@code null} if it couldn't be guessed
     */
    private String guessBookNameFromUri(Uri uri) {
        String fileName = BookName.getFileName(this, uri);

        if (fileName != null && BookName.isSupportedFormatFileName(fileName)) {
            BookName bookName = BookName.fromFileName(fileName);
            return bookName.getName();

        } else {
            return null;
        }
    }

    /**
     * Note has been clicked in list view.
     *
     * @param fragment Fragment from which the action came.
     * @param view     view
     * @param position note position in list
     * @param noteId   note ID
     */
    @Override
    public void onNoteClick(NoteListFragment fragment, View view, int position, long id, long noteId) {
        if (AppPreferences.isReverseNoteClickAction(this)) {
            toggleNoteSelection(fragment, view, id);

        } else {
            /* If there are any selected notes, toggle the selection of this one.
             * If there are no notes selected, open note.
             */
            if (fragment.getSelection().getCount() > 0) {
                toggleNoteSelection(fragment, view, id);
            } else {
                openNote(noteId);
            }
        }
    }

    /**
     * Note has been long-clicked in list view.
     *
     * @param fragment Fragment from which the action came.
     * @param view     view
     * @param position note position in list
     * @param noteId   note ID
     */
    @Override
    public void onNoteLongClick(NoteListFragment fragment, View view, int position, long id, long noteId) {
        if (AppPreferences.isReverseNoteClickAction(this)) {
            openNote(noteId);
        } else {
            toggleNoteSelection(fragment, view, id);
        }
    }

    private void openNote(long noteId) {
        finishActionMode();

        // TODO: Avoid using Shelf from activity directly
        Shelf shelf = new Shelf(this);
        Note note = shelf.getNote(noteId);

        if (note != null) {
            long bookId = note.getPosition().getBookId();

            Intent intent = new Intent(AppIntent.ACTION_OPEN_NOTE);
            intent.putExtra(AppIntent.EXTRA_NOTE_ID, noteId);
            intent.putExtra(AppIntent.EXTRA_BOOK_ID, bookId);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }
    }

    @Override
    public void onNoteScrollToRequest(long noteId) {
        // TODO: Avoid using Shelf from activity directly
        Shelf shelf = new Shelf(this);
        Note note = shelf.getNote(noteId);

        if (note != null) {
            long bookId = note.getPosition().getBookId();
            mSyncFragment.sparseTree(bookId, noteId);
            DisplayManager.displayBook(getSupportFragmentManager(), bookId, noteId);
        }
    }

    /**
     * Toggle selection of a note (or notes, if selected note is folded).
     */
    private void toggleNoteSelection(NoteListFragment fragment, View view, long noteId) {
        Selection selection = fragment.getSelection();

        selection.toggle(view, noteId);

        updateActionModeForSelection(fragment.getSelection().getCount(), fragment.getNewActionMode());
    }

    /* Open note fragment to create a new note. */
    @Override
    public void onNoteNewRequest(NotePlace target) {
        finishActionMode();

        DisplayManager.displayNewNote(getSupportFragmentManager(), target);
    }

    /* Save note. */
    @Override
    public void onNoteCreateRequest(Note note, NotePlace notePlace) {
        finishActionMode();

        popBackStackAndCloseKeyboard();

        mSyncFragment.createNote(note, notePlace);
    }

    @Override
    public void onNoteCreated(final Note note) {
        /*
         * Display Snackbar with an action - create new note below just created one.
         */
        View view = findViewById(R.id.main_content);
        if (view != null) {
            showSnackbar(Snackbar
                    .make(view, R.string.message_note_created, MiscUtils.SNACKBAR_WITH_ACTION_DURATION)
                    .setAction(R.string.new_below, v -> {
                        NotePlace notePlace = new NotePlace(
                                note.getPosition().getBookId(),
                                note.getId(),
                                Place.BELOW);

                        DisplayManager.displayNewNote(getSupportFragmentManager(), notePlace);
                    }));
        }


        /* Animate updated note. */
//        animateNotesAfterEdit(MiscUtils.set(note.getId()));
    }

    @Override
    public void onNoteCreatingFailed() {
        showSimpleSnackbarLong(R.string.message_failed_creating_note);
    }

    @Override
    public void onNoteUpdateRequest(Note note) {
        popBackStackAndCloseKeyboard();
        mSyncFragment.updateNote(note);
    }

    @Override
    public void onNoteUpdated(Note note) {
    }

    @Override
    public void onNoteUpdatingFailed(Note note) {
        showSimpleSnackbarLong(R.string.message_failed_updating_note);
    }

    @Override
    public void onNoteCancelRequest(Note note) {
        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onNoteDeleteRequest(Note note) {
        popBackStackAndCloseKeyboard();
        mSyncFragment.deleteNotes(note.getPosition().getBookId(), note.getId());
    }

    @Override
    public void onStateChangeRequest(Set<Long> noteIds, String state) {
        mSyncFragment.updateNoteState(noteIds, state);
    }

    @Override
    public void onStateFlipRequest(long noteId) {
        mSyncFragment.flipState(noteId);
    }

    @Override
    public void onScheduledTimeUpdateRequest(Set<Long> noteIds, OrgDateTime time) {
        mSyncFragment.updateScheduledTime(noteIds, time);
    }

    @Override
    public void onCycleVisibilityRequest(Book book) {
        mSyncFragment.cycleVisibility(book);
    }

    @Override
    public void onScheduledTimeUpdated(Set<Long> noteIds, OrgDateTime time) {
        // animateNotesAfterEdit(noteIds);
    }

    @Override /* BookFragment */
    public void onBookPrefaceEditRequest(Book book) {
        finishActionMode();

        DisplayManager.displayEditor(getSupportFragmentManager(), book);
    }

    @Override
    public void onNotesDeleteRequest(final long bookId, final Set<Long> noteIds) {
        mSyncFragment.deleteNotes(bookId, noteIds);
    }

    @Override
    public void onNotesDeleted(int count) {
        String message;

        if (count == 0) {
            message = getResources().getString(R.string.no_notes_deleted);
        } else {
            message = getResources().getQuantityString(R.plurals.notes_deleted, count, count);
        }

        showSimpleSnackbarLong(message);
    }

    @Override
    public void onNotesCutRequest(long bookId, Set<Long> noteIds) {
        mSyncFragment.cutNotes(bookId, noteIds);
    }

    @Override
    public void onNotesCut(int count) {
        String message;

        if (count == 0) {
            message = getResources().getString(R.string.no_notes_cut);
        } else {
            message = getResources().getQuantityString(R.plurals.notes_cut, count, count);
        }

        showSimpleSnackbarLong(message);
    }

    /**
     * Ask user to confirm, then delete book.
     */
    @Override
    public void onBookDeleteRequest(final long bookId) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Get name for the book " + bookId + "...");

        final Book book = BooksClient.get(this, bookId);

        if (book == null) {
            return;
        }

        View view = View.inflate(this, R.layout.dialog_book_delete, null);
        final CheckBox checkBox = view.findViewById(R.id.checkbox);

        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    boolean deleteLinked = checkBox.isChecked();

                    /* Delete book. */
                    mSyncFragment.deleteBook(book, deleteLinked);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle("Delete " + MiscUtils.quotedString(book.getName()))
                        .setMessage("Delete this notebook?")
                        .setPositiveButton(R.string.ok, dialogClickListener)
                        .setNegativeButton(R.string.cancel, dialogClickListener);

        if (book.hasRook()) {
            builder.setView(view);
        }

        builder.show();

    }

    @Override
    public void onBookRenameRequest(final long bookId) {
        final Book book = BooksClient.get(this, bookId);

        if (book == null) {
            return;
        }

        final View dialogView = View.inflate(this, R.layout.dialog_book_rename, null);
        final TextInputLayout nameInputLayout = dialogView.findViewById(R.id.name_input_layout);
        final EditText name = dialogView.findViewById(R.id.name);

        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    doRenameBook(book, name.getText().toString(), nameInputLayout);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.rename_book, MiscUtils.quotedString(book.getName())))
                        .setPositiveButton(R.string.rename, dialogClickListener)
                        .setNegativeButton(R.string.cancel, dialogClickListener)
                        .setView(dialogView);

        name.setText(book.getName());

        final AlertDialog dialog = builder.create();

        /* Finish on keyboard action press. */
        name.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });


        final Activity activity = this;

        dialog.setOnShowListener(d -> ActivityUtils.INSTANCE.openSoftKeyboard(activity, name));

        dialog.setOnDismissListener(d -> ActivityUtils.INSTANCE.closeSoftKeyboard(activity));

        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable str) {
                /* Disable the button is nothing is entered. */
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(str));
            }
        });

        dialog.show();
    }

    private void doRenameBook(Book book, String name, TextInputLayout inputLayout) {
        if (!TextUtils.isEmpty(name)) {
            inputLayout.setError(null);
            mSyncFragment.renameBook(book, name);

        } else {
            inputLayout.setError(getString(R.string.can_not_be_empty));
        }
    }

    @Override
    public void onBookLinkSetRequest(final long bookId) {
        final Book book = BooksClient.get(this, bookId);

        if (book == null) {
            return;
        }

        Map<String, Repo> repos = ReposClient.getAll(this);

        if (repos.size() == 0) {
            showSnackbarWithReposLink(getString(R.string.no_repos));
            return;
        }

        LinkedHashMap<String, Integer> items = new LinkedHashMap<>();
        int itemIndex = 0;

        /* Add "no link" item. */
        items.put(getString(R.string.no_link), itemIndex++);

        /* Add repositories. */
        for (String repoUri : repos.keySet()) {
            items.put(repoUri, itemIndex++);
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_spinner, null, false);
        final Spinner spinner = (Spinner) view.findViewById(R.id.dialog_spinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(spinner.getContext(), R.layout.spinner_item, new ArrayList<>(items.keySet()));
        adapter.setDropDownViewResource(R.layout.dropdown_item);
        spinner.setAdapter(adapter);

        /* Set spinner to current book's link. */
        if (book.hasRook()) {
            Integer pos = items.get(book.getRook().getRepoUri().toString());
            if (pos != null) {
                spinner.setSelection(pos);
            }
        }

        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    Shelf shelf = new Shelf(MainActivity.this);
                    String repoUrl = (String) spinner.getSelectedItem();

                    if (getString(R.string.no_link).equals(repoUrl)) {
                        shelf.setLink(book, null);
                    } else {
                        shelf.setLink(book, repoUrl);
                    }

                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        new AlertDialog.Builder(this)
                .setTitle("Link " + MiscUtils.quotedString(book.getName()) + " to repository")
                .setView(view)
                .setPositiveButton(R.string.set, dialogClickListener)
                .setNegativeButton(R.string.cancel, dialogClickListener)
                .show();
    }

    @Override
    public void onForceSaveRequest(long bookId) {
        mSyncFragment.forceSaveBook(bookId);
    }

    @Override
    public void onBookSaved(Book book) {
    }

    @Override
    public void onBookForceSavingFailed(Exception exception) {
    }

    @Override
    public void onForceLoadRequest(long bookId) {
        mSyncFragment.loadBook(bookId);
    }

    /**
     * Callback from {@link com.orgzly.android.ui.fragments.BooksFragment}.
     */
    @Override
    public void onBookExportRequest(final long bookId) {
        runWithPermission(
                AppPermissions.Usage.BOOK_EXPORT,
                () -> mSyncFragment.exportBook(bookId));
    }

    @Override
    public void onBookImportRequest() {
        startFileChooser(R.string.import_org_file, ACTIVITY_REQUEST_CODE_FOR_BOOK_IMPORT);
    }

    @Override
    public void onFiltersExportRequest(int title, @NonNull String message) {
        runWithPermission(
                AppPermissions.Usage.FILTERS_EXPORT_IMPORT,
                () -> mSyncFragment.exportQueries());
    }

    @Override
    public void onFiltersImportRequest(int title, @NonNull String message) {
        startFileChooser(R.string.import_, ACTIVITY_REQUEST_CODE_FOR_QUERIES_IMPORT);
    }

    private void startFileChooser(@StringRes int titleResId, int code) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(Intent.createChooser(intent, getString(titleResId)), code);
    }

    /**
     * Prompt user for book name and then create it.
     */
    @Override
    public void onBookCreateRequest() {
        SimpleOneLinerDialog
                .getInstance(DIALOG_NEW_BOOK, R.string.new_notebook, R.string.name, R.string.create, R.string.cancel, null, null)
                .show(getSupportFragmentManager(), SimpleOneLinerDialog.FRAGMENT_TAG);
    }

    @Override /* SyncFragment */
    public void onBookCreated(Book book) {
    }

    @Override /* SyncFragment */
    public void onBookCreationFailed(Exception exception) {
        showSimpleSnackbarLong(exception.getMessage());
    }

    /**
     * Sync finished.
     *
     * Display Snackbar with a message.  If it makes sense also set action to open a repository.
     *
     * @param msg Error message if syncing failed, null if it was successful
     */
    @Override
    public void onSyncFinished(String msg) {
        if (msg != null) {
            showSnackbarWithReposLink(getString(R.string.sync_with_argument, msg));
        }
    }

    @Override /* SyncFragment */
    public void onBookExported(File file) {
        showSimpleSnackbarLong(getString(R.string.book_exported, file.getAbsolutePath()));
    }

    /**
     * Display snackbar and include link to repositories.
     */
    private void showSnackbarWithReposLink(String msg) {
        View view = findViewById(R.id.main_content);

        if (view != null) {
            showSnackbar(Snackbar.make(view, msg, MiscUtils.SNACKBAR_WITH_ACTION_DURATION)
                    .setAction(R.string.repositories, v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setClass(MainActivity.this, ReposActivity.class);
                        startActivity(intent);
                    }));
        }
    }

    @Override /* SyncFragment */
    public void onBookExportFailed(Exception e) {
        showSimpleSnackbarLong(getString(R.string.failed_exporting_book, e.getLocalizedMessage()));
    }

    @Override
    public void onNotesPasteRequest(long bookId, long noteId, Place place) {
        mSyncFragment.pasteNotes(bookId, noteId, place);
    }

    @Override
    public void onNotesPromoteRequest(long bookId, Set<Long> noteIds) {
        mPromoteDemoteOrMoveRequested = true;
        mSyncFragment.promoteNotes(bookId, noteIds);
    }

    @Override
    public void onNotesDemoteRequest(long bookId, Set<Long> noteIds) {
        mPromoteDemoteOrMoveRequested = true;
        mSyncFragment.demoteNotes(bookId, noteIds);
    }

    @Override
    public void onNotesMoveRequest(long bookId, long noteId, int offset) {
        mPromoteDemoteOrMoveRequested = true;
        mSyncFragment.moveNote(bookId, noteId, offset);
    }

    @Override
    public void onNotesMoved(int count) {
    }

    @Override
    public void onFailure(String message) {
        showSimpleSnackbarLong(message);
    }

    @Override
    public void onNotesPasted(NotesBatch batch) {
        String message = getResources().getQuantityString(
                R.plurals.notes_pasted,
                batch.getCount(),
                batch.getCount());

        showSimpleSnackbarLong(message);
    }

    @Override
    public void onNotesNotPasted() {
        showSimpleSnackbarLong(getResources().getString(R.string.no_notes_pasted));
    }

    @Override /* SyncFragment */
    public void onBookDeleted(Book book) {
        showSimpleSnackbarLong(R.string.message_book_deleted);
    }

    @Override
    public void onBookDeletingFailed(Book book, IOException exception) {
        String message = getResources().getString(
                R.string.message_deleting_book_failed,
                exception.toString());

        showSimpleSnackbarLong(message);
    }

    @Override
    public void onBookClicked(long bookId) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, bookId);

        /* Attempt to avoid occasional rare IllegalStateException (state loss related).
         * Consider removing BooksFragmentListener and using broadcasts for all actions instead.
         */
        // DisplayManager.displayBook(bookId, 0);
        Intent intent = new Intent(AppIntent.ACTION_OPEN_BOOK);
        intent.putExtra(AppIntent.EXTRA_BOOK_ID, bookId);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

//    private void animateNotesAfterEdit(Set<Long> noteIds) {
//        Fragment f;
//
//        f = getSupportFragmentManager().findFragmentByTag(BookFragment.FRAGMENT_TAG);
//        if (f != null && f.isVisible()) {
//            BookFragment heads = (BookFragment) f;
//            heads.animateNotes(noteIds, HeadAnimator.ANIMATE_FOR_HEAD_MODIFIED);
//        }
//
//        f = getSupportFragmentManager().findFragmentByTag(QueryFragment.FRAGMENT_TAG);
//        if (f != null && f.isVisible()) {
//            QueryFragment heads = (QueryFragment) f;
//            heads.animateNotes(noteIds, HeadAnimator.ANIMATE_FOR_HEAD_MODIFIED);
//        }
//    }

    @Override /* EditorFragment */
    public void onBookPrefaceEditSaveRequest(Book book) {
        popBackStackAndCloseKeyboard();
        mSyncFragment.updateBookSettings(book);
    }

    @Override
    public void onBookPrefaceEditCancelRequest() {
        popBackStackAndCloseKeyboard();
    }

    // TODO: Implement handlers when dialog is created
    @Override
    public void onSimpleOneLinerDialogValue(int id, String value, Bundle userData) {
        switch (id) {
            case DIALOG_NEW_BOOK:
                mSyncFragment.createNewBook(value);
                break;

            case DIALOG_IMPORT_BOOK:
                Uri uri = Uri.parse(userData.getString("uri"));
                /* We are assuming it's an Org file. */
                mSyncFragment.importBookFromUri(value, BookName.Format.ORG, uri);
                break;
        }
    }

    private Book getActiveFragmentBook() {
        Fragment f = getSupportFragmentManager().findFragmentByTag(BookFragment.FRAGMENT_TAG);

        if (f != null && f.isVisible()) {
            BookFragment bookFragment = (BookFragment) f;
            return bookFragment.getBook();
        }

        return null;
    }

    /*
     * Action mode
     */

    @Override
    public void updateActionModeForSelection(int selectedCount, ActionMode.Callback actionMode) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, selectedCount, actionMode);

        if (mActionMode != null) { /* Action menu is already activated. */
            /* Finish action mode if there are no more selected items. */
            if (selectedCount == 0) {
                mActionMode.finish();
            } else {
                mActionMode.invalidate();
            }

        } else { /* No action menu activated - started it. */
            if (selectedCount > 0) {
                /* Start new action mode. */
                mActionMode = startSupportActionMode(actionMode);

                /* Lock drawer. Do not allow swipe-to-open while CAB is active. */
                if (mDrawerLayout != null) {
                    mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                }
            }
        }
    }

    @Override
    public ActionMode getActionMode() {
        return mActionMode;
    }

    @Override
    public void actionModeDestroyed() {
        if (mActionMode != null) {
            if ("M".equals(mActionMode.getTag()) && mPromoteDemoteOrMoveRequested) {
                Shelf shelf = new Shelf(this);
                shelf.syncOnNoteUpdate();
            }
        }
        mPromoteDemoteOrMoveRequested = false;
        mActionMode = null;

        /* Unlock drawer. */
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
    }

    private void finishActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    /**
     * Updates colors, FAB, title and subtitle, all depending on displayed fragment.
     */
    @Override
    public void announceChanges(String fragmentTag, CharSequence title, CharSequence subTitle, int selectionCount) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, fragmentTag, title, subTitle, selectionCount);

        /* Save titles so they can be restored after drawer open/close. */
        mSavedTitle = title != null ? title : mDefaultTitle;
        mSavedSubtitle = subTitle;

        /* Change titles. */
        getSupportActionBar().setTitle(mSavedTitle);
        getSupportActionBar().setSubtitle(mSavedSubtitle);

        drawerNavigationView.updateActiveFragment(fragmentTag);

        /* Update floating action button. */
        MainFab.INSTANCE.updateFab(this, fragmentTag, selectionCount);
    }

    @Override
    public void onFilterNewRequest() {
        DisplayManager.onFilterNewRequest(getSupportFragmentManager());
    }

    @Override
    public void onFilterDeleteRequest(Set<Long> ids) {
        mSyncFragment.deleteFilters(ids);
    }

    @Override
    public void onFilterEditRequest(long id) {
        DisplayManager.onFilterEditRequest(getSupportFragmentManager(), id);
    }

    @Override
    public void onFilterMoveUpRequest(long id) {
        mSyncFragment.moveFilterUp(id);
    }

    @Override
    public void onFilterMoveDownRequest(long id) {
        mSyncFragment.moveFilterDown(id);
    }

    @Override
    public void onFilterCreateRequest(Filter filter) {
        popBackStackAndCloseKeyboard();
        mSyncFragment.createFilter(filter);
    }

    @Override
    public void onFilterUpdateRequest(long id, Filter filter) {
        popBackStackAndCloseKeyboard();
        mSyncFragment.updateFilter(id, filter);
    }

    @Override
    public void onFilterCancelRequest() {
        popBackStackAndCloseKeyboard();
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent);

            if (intent != null && intent.getAction() != null) {
                handleIntent(intent, intent.getAction());
            }
        }

        private void handleIntent(@NonNull Intent intent, @NonNull String action) {
            switch (action) {
                case AppIntent.ACTION_OPEN_NOTE: {
                    long bookId = intent.getLongExtra(AppIntent.EXTRA_BOOK_ID, 0);
                    long noteId = intent.getLongExtra(AppIntent.EXTRA_NOTE_ID, 0);
                    DisplayManager.displayNote(getSupportFragmentManager(), bookId, noteId);
                    break;
                }

                case AppIntent.ACTION_OPEN_QUERIES: {
                    DisplayManager.displayFilters(getSupportFragmentManager());
                    break;
                }

                case AppIntent.ACTION_OPEN_QUERY: {
                    String query = intent.getStringExtra(AppIntent.EXTRA_QUERY_STRING);
                    DisplayManager.displayQuery(getSupportFragmentManager(), query);
                    break;
                }

                case AppIntent.ACTION_OPEN_BOOKS: {
                    DisplayManager.displayBooks(getSupportFragmentManager(), true);
                    break;
                }

                case AppIntent.ACTION_OPEN_BOOK: {
                    long bookId = intent.getLongExtra(AppIntent.EXTRA_BOOK_ID, 0);
                    DisplayManager.displayBook(getSupportFragmentManager(), bookId, 0);
                    break;
                }

                case AppIntent.ACTION_OPEN_SETTINGS: {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    break;
                }
            }

        }
    }
}
