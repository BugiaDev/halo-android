package com.mobgen.halo.android.app.ui.batchimages;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.bluelinelabs.logansquare.JsonMapper;
import com.bluelinelabs.logansquare.LoganSquare;
import com.mobgen.halo.android.app.R;
import com.mobgen.halo.android.app.model.BatchImage;
import com.mobgen.halo.android.app.ui.MobgenHaloActivity;
import com.mobgen.halo.android.app.ui.MobgenHaloApplication;
import com.mobgen.halo.android.app.utils.StatusInterceptor;
import com.mobgen.halo.android.app.utils.ViewUtils;
import com.mobgen.halo.android.content.HaloContentApi;
import com.mobgen.halo.android.content.edition.HaloContentEditApi;
import com.mobgen.halo.android.content.models.BatchOperationResults;
import com.mobgen.halo.android.content.models.BatchOperations;
import com.mobgen.halo.android.content.models.HaloContentInstance;
import com.mobgen.halo.android.content.models.Paginated;
import com.mobgen.halo.android.content.models.SearchQuery;
import com.mobgen.halo.android.content.models.SearchSort;
import com.mobgen.halo.android.content.models.SortField;
import com.mobgen.halo.android.content.models.SortOrder;
import com.mobgen.halo.android.content.search.SearchQueryBuilderFactory;
import com.mobgen.halo.android.framework.toolbox.data.CallbackV2;
import com.mobgen.halo.android.framework.toolbox.data.Data;
import com.mobgen.halo.android.framework.toolbox.data.HaloResultV2;
import com.mobgen.halo.android.framework.toolbox.data.HaloStatus;
import net.bohush.geometricprogressview.GeometricProgressView;

import java.util.ArrayList;
import java.util.List;

import icepick.Icepick;
import icepick.State;

import static com.mobgen.halo.android.app.ui.batchimages.GalleryBatchImageActivity.CODE_ACTIVITY;

public class BatchImageActivity extends MobgenHaloActivity implements SwipeRefreshLayout.OnRefreshListener, BatchImageAdapter.TextChangeListener {

    private static final String BUNDLE_MODULE_NAME = "bundle_module_name";
    private static final String BUNDLE_MODULE_ID = "bundle_module_id";

    @State
    ArrayList<BatchImage> mGalleryImages;
    @State
    HaloStatus mStatus;
    @State
    String mModuleName;
    @State
    String mModuleId;
    private boolean deleteVisible = false;
    private View mStatusView;
    private SwipeRefreshLayout mSwipeToRefresh;
    private RecyclerView mRecyclerView;
    private BatchImageAdapter mAdapter;
    private Context mContext;
    private GeometricProgressView mProgressView;
    private SearchSort mSearchSort;
    private Boolean[] isAscending = new Boolean[]{false, false, false, false, false, false, false};

    public static void start(@NonNull Context context, @NonNull String moduleName, @NonNull String moduleId) {
        Bundle data = new Bundle();
        data.putString(BUNDLE_MODULE_NAME, moduleName);
        data.putString(BUNDLE_MODULE_ID, moduleId);
        Intent intent = new Intent(context, BatchImageActivity.class);
        intent.putExtras(data);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        mModuleName = getIntent().getExtras().getString(BUNDLE_MODULE_NAME);
        mModuleId = getIntent().getExtras().getString(BUNDLE_MODULE_ID);
        mContext = this;
        Icepick.restoreInstanceState(this, savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (mModuleName == null) {
            throw new IllegalStateException("You have to provide the module name to start the activity.");
        }
        mAdapter = new BatchImageAdapter(this, this);
        mSwipeToRefresh = (SwipeRefreshLayout) findViewById(R.id.srl_generic);
        mStatusView = findViewById(R.id.v_status);
        mRecyclerView = (RecyclerView) findViewById(R.id.rv_generic);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mProgressView = (GeometricProgressView) findViewById(R.id.gm_progress);

        mSearchSort = new SearchSort(SortField.PUBLISHED, SortOrder.ASCENDING);
    }

    @Override
    public void onPresenterInitialized() {
        super.onPresenterInitialized();
        mRecyclerView.setAdapter(mAdapter);
        mSwipeToRefresh.setOnRefreshListener(this);
        if (mGalleryImages == null) {
            loadGallery();
        } else {
            updateGallery();
        }
    }

    private void loadGallery() {
        mGalleryImages = new ArrayList<>();
        ViewUtils.refreshing(mSwipeToRefresh, true);
        SearchQuery options = SearchQueryBuilderFactory.getPublishedItems(mModuleName, mModuleName)
                .onePage(true)
                .sort(mSearchSort)
                .serverCache(60 * 2)
                .segmentWithDevice()
                .build();
        HaloContentApi.with(MobgenHaloApplication.halo())
                .search(Data.NETWORK_AND_STORAGE, options)
                .asContent()
                .execute(new CallbackV2<Paginated<HaloContentInstance>>() {
                    @Override
                    public void onFinish(@NonNull HaloResultV2<Paginated<HaloContentInstance>> result) {
                        ViewUtils.refreshing(mSwipeToRefresh, false);
                        // mLoadingAction = null;
                        mStatus = result.status();
                        if (mStatus.isOk()) {
                            List<HaloContentInstance> data = result.data().data();
                            if (data != null) {
                                JsonMapper<BatchImage> mapper = LoganSquare.mapperFor(BatchImage.class);
                                for (int j = 0; j < data.size(); j++) {
                                    try {
                                        BatchImage batchImage = mapper.parse(data.get(j).getValues().toString());
                                        batchImage.setInstanceId(data.get(j).getItemId());
                                        mGalleryImages.add(batchImage);
                                    } catch (Exception e) {
                                    }
                                }
                                updateGallery();
                            }
                        }
                    }
                });
    }

    private void updateGallery() {
        if (mStatus != null) {
            StatusInterceptor.intercept(mStatus, mStatusView);
        }
        mAdapter.setImages(mGalleryImages);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public String getToolbarTitle() {
        return getString(R.string.batch_gallery_title);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    public void onRefresh() {
        loadGallery();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == CODE_ACTIVITY) {
            if (resultCode == RESULT_OK) {
                loadGallery();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_batch, menu);
        MenuItem deleteItem = menu.findItem(R.id.action_delete_batch);
        if (deleteVisible) {
            deleteItem.setVisible(true);
        } else {
            deleteItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_add_batch:
                //open remote batch gallery to add elements
                GalleryBatchImageActivity.start(this, mModuleName, mModuleId);
                break;
            case R.id.action_edit_batch:
                //update instances on a batch operation
                mProgressView.setVisibility(View.VISIBLE);
                BatchOperations updateOperations = getAllInstancesToBatch();
                if (updateOperations != null) {
                    HaloContentEditApi.with(MobgenHaloApplication.halo())
                            .batch(updateOperations, false)
                            .execute(new CallbackV2<BatchOperationResults>() {
                                @Override
                                public void onFinish(@NonNull HaloResultV2<BatchOperationResults> result) {
                                    mProgressView.setVisibility(View.GONE);
                                    if (result.status().isOk()) {
                                        //refresh the list
                                        loadGallery();
                                    } else {
                                        Toast.makeText(BatchImageActivity.this, "We have some problems with the batch request", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                } else {
                    mProgressView.setVisibility(View.GONE);
                    Toast.makeText(BatchImageActivity.this, "Sorry we cannot make the operation", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_delete_batch:
                //delete instances on a batch operation
                mProgressView.setVisibility(View.VISIBLE);
                BatchOperations deleteOperations = getInstancesToBatch();
                if (deleteOperations != null) {
                    HaloContentEditApi.with(MobgenHaloApplication.halo())
                            .batch(deleteOperations, false)
                            .execute(new CallbackV2<BatchOperationResults>() {
                                @Override
                                public void onFinish(@NonNull HaloResultV2<BatchOperationResults> result) {
                                    mProgressView.setVisibility(View.GONE);
                                    if (result.status().isOk()) {
                                        //refresh the list
                                        loadGallery();
                                    } else {
                                        Toast.makeText(BatchImageActivity.this, "We have some problems with the batch request", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                } else {
                    mProgressView.setVisibility(View.GONE);
                    Toast.makeText(BatchImageActivity.this, "Sorry we cannot make the operation", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_sort_batch_name:
                setSearchSortOptions(SortField.NAME, 0);
                break;
            case R.id.action_sort_batch_create:
                setSearchSortOptions(SortField.CREATED, 1);
                break;
            case R.id.action_sort_batch_update:
                setSearchSortOptions(SortField.UPDATED, 2);
                break;
            case R.id.action_sort_batch_publish:
                setSearchSortOptions(SortField.PUBLISHED, 3);
                break;
            case R.id.action_sort_batch_remove:
                setSearchSortOptions(SortField.REMOVED, 4);
                break;
            case R.id.action_sort_batch_archive:
                setSearchSortOptions(SortField.ARCHIVED, 5);
                break;
            case R.id.action_sort_batch_delete:
                setSearchSortOptions(SortField.DELETED, 6);
                break;
        }
        return true;
    }

    private void setSearchSortOptions(@NonNull @SortField.SortOperator String field, int index) {
        //change sort order
        if (isAscending[index]) {
            mSearchSort = new SearchSort(field, SortOrder.DESCENDING);
        } else {
            mSearchSort = new SearchSort(field, SortOrder.ASCENDING);
        }
        //change status
        isAscending[index] = !isAscending[index];
        //load images
        loadGallery();
    }

    @Nullable
    public BatchOperations getAllInstancesToBatch() {
        if (mGalleryImages != null) {
            BatchOperations.Builder operations = BatchOperations.builder();
            for (int i = 0; i < mGalleryImages.size(); i++) {
                //add one instance with every image which has been selected
                HaloContentInstance instance = new HaloContentInstance.Builder(mModuleName)
                        .withAuthor("Android SDK app")
                        .withContentData(mGalleryImages.get(i))
                        .withName("Create from batch request")
                        .withId(mGalleryImages.get(i).getInstanceId())
                        .withModuleId(mModuleId)
                        .build();
                operations.update(instance);
            }
            return operations.build();
        } else {
            return null;
        }
    }

    @Nullable
    public BatchOperations getInstancesToBatch() {
        if (mGalleryImages != null) {
            BatchOperations.Builder operations = BatchOperations.builder();
            for (int i = 0; i < mGalleryImages.size(); i++) {
                //add one instance with every image which has been selected
                if (mGalleryImages.get(i).isSelected()) {
                    HaloContentInstance instance = new HaloContentInstance.Builder(mModuleName)
                            .withAuthor("Android SDK app")
                            .withContentData(mGalleryImages.get(i))
                            .withName("Create from batch request")
                            .withId(mGalleryImages.get(i).getInstanceId())
                            .withModuleId(mModuleId)
                            .build();
                    operations.delete(instance);
                }
            }
            return operations.build();
        } else {
            return null;
        }
    }

    @Override
    public void onTextChange(BatchImage batchImage, int position) {
        Log.v("new name", batchImage.author());
    }

    @Override
    public void onItemSelected(boolean isSelected) {
        if (isSelected) {
            deleteVisible = true;
            invalidateOptionsMenu();
        } else {
            boolean visible = false;
            for (int i = 0; i < mGalleryImages.size(); i++) {
                if (mGalleryImages.get(i).isSelected()) {
                    visible = true;
                    return;
                }
            }
            if (!visible) {
                deleteVisible = false;
                invalidateOptionsMenu();
            }
        }
    }
}
