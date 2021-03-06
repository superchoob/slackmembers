package bschen.slackprofile;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import bschen.slackprofile.models.Member;
import bschen.slackprofile.models.MembersResponse;
import bschen.slackprofile.utils.DataUtils;
import butterknife.Bind;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.GsonConverterFactory;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MembersActivity extends AppCompatActivity {

    private static final String KEY_MEMBERS = "KEY_MEMBERS";

    SlackService mSlackService;
    MembersAdapter mAdapter;

    @Bind(R.id.members_list) ListView mListView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_members);
        ButterKnife.bind(this);

        mSlackService = initSlackService();
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void initViews() {
        mAdapter = new MembersAdapter(this, new ArrayList<Member>());
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view,
                    final int position, final long id) {
                launchProfileActivity(mAdapter.getItem(position), view);
            }
        });
    }

    private SlackService initSlackService() {
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SlackService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(SlackService.class);
    }

    private void loadData() {
        final Call<MembersResponse> call = mSlackService.listMembers(SlackService.TOKEN);
        call.enqueue(new Callback<MembersResponse>() {
            @Override
            public void onResponse(final Response<MembersResponse> response) {
                final MembersResponse memberResponse = response.body();
                if (response.body() != null && memberResponse.isOk()) {
                    final List<Member> members = memberResponse.getMembers();
                    saveMembersToCache(members);
                    mAdapter.replaceMembers(members);
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                // If no response was received, load the most recently cached members list
                mAdapter.replaceMembers(loadMembersFromCache());
                Toast.makeText(MembersActivity.this, R.string.error_network, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void launchProfileActivity(final Member member, final View listItemView) {
        final Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_MEMBER, member);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            startActivity(intent);
        } else {
            // Trying out the Android Scene Transition API. Looks like it's still in development
            // and has some kinks in it.
            // For example, the nav and status bars flash while transitioning. There's a
            // workaround, but it's messy so I didn't include it.
            final Object tag = listItemView.getTag();
            if (tag instanceof MembersAdapter.ViewHolder) {
                final MembersAdapter.ViewHolder viewHolder = (MembersAdapter.ViewHolder) tag;
                final View avatar = viewHolder.avatar;
                final View userName = viewHolder.userName;
                final View realName = viewHolder.realName;

                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        Pair.create(avatar, avatar.getTransitionName()),
                        Pair.create(userName, userName.getTransitionName()),
                        Pair.create(realName, realName.getTransitionName()));

                startActivity(intent, options.toBundle());
            }
        }
    }

    private void saveMembersToCache(final List<Member> members) {
        try {
            DataUtils.writeObject(this, KEY_MEMBERS, members);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private List<Member> loadMembersFromCache() {
        List<Member> members = null;
        try {
            members = (List<Member>) DataUtils.readObject(this, KEY_MEMBERS);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return members;
    }
}
