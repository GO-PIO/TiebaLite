package com.huanchengfly.tieba.post

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import butterknife.BindView
import butterknife.OnClick
import com.huanchengfly.tieba.api.TiebaApi
import com.huanchengfly.tieba.api.models.SubFloorListBean
import com.huanchengfly.tieba.post.activities.ReplyActivity
import com.huanchengfly.tieba.post.activities.base.BaseActivity
import com.huanchengfly.tieba.post.adapters.RecyclerFloorAdapter
import com.huanchengfly.tieba.post.components.MyLinearLayoutManager
import com.huanchengfly.tieba.post.components.dividers.ThreadDivider
import com.huanchengfly.tieba.post.models.ReplyInfoBean
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.NavigationHelper
import com.huanchengfly.tieba.post.utils.ThemeUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FloorActivity : BaseActivity() {
    @BindView(R.id.toolbar)
    lateinit var toolbar: Toolbar
    @BindView(R.id.floor_refresh_view)
    lateinit var refreshLayout: SwipeRefreshLayout
    @BindView(R.id.floor_recycler_view)
    lateinit var recyclerView: RecyclerView
    private var dataBean: SubFloorListBean? = null
    private var recyclerViewAdapter: RecyclerFloorAdapter? = null
    private var navigationHelper: NavigationHelper? = null
    private var tid: String? = null
    private var pid: String? = null
    private var spid: String? = null
    private var hasMore = false
    private var pn = 1
    private val replyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == ThreadActivity.ACTION_REPLY_SUCCESS) {
                val pid = intent.getStringExtra("pid")
                if (pid == this@FloorActivity.pid) {
                    refresh()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter()
        filter.addAction(ThreadActivity.ACTION_REPLY_SUCCESS)
        registerReceiver(replyReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(replyReceiver)
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_floor
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.setTranslucentThemeBackground(findViewById(R.id.background))
        navigationHelper = NavigationHelper.newInstance(this)
        initView()
        if (savedInstanceState == null) {
            initData()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_floor_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_to_thread -> {
                if (dataBean != null) {
                    navigationHelper!!.navigationByData(NavigationHelper.ACTION_THREAD, mapOf<String, String>(
                            "tid" to tid!!,
                            "pid" to pid!!
                    ))
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initData() {
        val intent = intent
        tid = intent.getStringExtra("tid")
        pid = intent.getStringExtra("pid")
        spid = intent.getStringExtra("spid")
        if (tid != null && (pid != null || spid != null)) {
            hasMore = true
            refresh()
        }
    }

    private fun initView() {
        setSupportActionBar(toolbar)
        supportActionBar?.setTitle(R.string.title_floor)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recyclerViewAdapter = RecyclerFloorAdapter(this).apply {
            openAutoLoadMore()
            setLoadingView(R.layout.layout_footer_loading)
            setLoadEndView(R.layout.layout_footer_loadend)
            setLoadFailedView(R.layout.layout_footer_load_failed)
            setOnLoadMoreListener { isReload: Boolean ->
                if (isReload) {
                    refresh()
                } else {
                    loadMore()
                }
            }
        }
        recyclerView.apply {
            layoutManager = MyLinearLayoutManager(this@FloorActivity)
            adapter = recyclerViewAdapter
            addItemDecoration(ThreadDivider(this@FloorActivity))
        }
        refreshLayout.apply {
            ThemeUtil.setThemeForSwipeRefreshLayout(this)
            setOnRefreshListener { refresh() }
        }
    }

    @OnClick(R.id.floor_reply_bar)
    fun onReplyBarClick(view: View) {
        if (dataBean == null) {
            return
        }
        val floor = dataBean!!.post!!.floor.toInt()
        val pn = floor - floor % 30
        startActivity(Intent(this, ReplyActivity::class.java)
                .putExtra("data",
                        ReplyInfoBean(dataBean!!.thread!!.id,
                                dataBean!!.forum!!.id,
                                dataBean!!.forum!!.name,
                                dataBean!!.anti!!.tbs,
                                dataBean!!.post!!.id,
                                dataBean!!.post!!.floor,
                                dataBean!!.post!!.author.nameShow,
                                AccountUtil.getLoginInfo(this).nameShow).setPn(pn.toString()).toString()))
    }

    private fun refresh() {
        refreshLayout.isRefreshing = true
        TiebaApi.getInstance().floor(tid!!, pn, pid, spid).enqueue(object : Callback<SubFloorListBean> {
            override fun onFailure(call: Call<SubFloorListBean>, t: Throwable) {
                Toast.makeText(this@FloorActivity, t.message, Toast.LENGTH_SHORT).show()
                recyclerViewAdapter!!.loadFailed()
                refreshLayout.isRefreshing = false
            }

            override fun onResponse(call: Call<SubFloorListBean>, response: Response<SubFloorListBean>) {
                val subFloorListBean = response.body() ?: return
                pid = subFloorListBean.post!!.id
                spid = null
                if (Integer.valueOf(subFloorListBean.page!!.currentPage) >= Integer.valueOf(subFloorListBean.page.totalPage)) {
                    hasMore = false
                    recyclerViewAdapter!!.loadEnd()
                }
                toolbar.title = getString(R.string.title_floor_loaded, subFloorListBean.post.floor)
                dataBean = subFloorListBean
                recyclerViewAdapter!!.setData(subFloorListBean)
                refreshLayout.isRefreshing = false
            }
        })
    }

    private fun loadMore() {
        if (hasMore) {
            TiebaApi.getInstance().floor(tid!!, pn, pid, spid).enqueue(object : Callback<SubFloorListBean> {
                override fun onFailure(call: Call<SubFloorListBean>, t: Throwable) {
                    recyclerViewAdapter!!.loadFailed()
                }

                override fun onResponse(call: Call<SubFloorListBean>, response: Response<SubFloorListBean>) {
                    val subFloorListBean = response.body()!!
                    pid = subFloorListBean.post!!.id
                    spid = null
                    if (Integer.valueOf(subFloorListBean.page!!.currentPage) >= Integer.valueOf(subFloorListBean.page.totalPage)) {
                        hasMore = false
                        recyclerViewAdapter!!.loadEnd()
                    }
                    pn += 1
                    dataBean = subFloorListBean
                    recyclerViewAdapter!!.addData(subFloorListBean)
                }
            })
        }
    }
}