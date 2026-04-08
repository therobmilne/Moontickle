package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.TentacleRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.shared.toolbar.LeftSidebarNavigation
import org.jellyfin.androidtv.ui.shared.toolbar.Navbar
import org.jellyfin.androidtv.ui.shared.toolbar.NavbarActiveButton
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber

class HomeFragment : Fragment() {
	private val tentacleRepository by inject<TentacleRepository>()
	private val api by inject<ApiClient>()
	private val userPreferences by inject<UserPreferences>()
	private val settingsViewModel by activityViewModel<SettingsViewModel>()

	private var titleView: TextView? = null
	private var logoView: ImageView? = null
	private var infoRowView: SimpleInfoRowView? = null
	private var summaryView: TextView? = null
	private var backgroundImage: ImageView? = null
	private var rowsFragment: HomeRowsFragment? = null
	private var snowfallView: SnowfallView? = null
	private var petalfallView: PetalfallView? = null
	private var leaffallView: LeaffallView? = null
	private var summerView: SummerView? = null
	private var halloweenView: HalloweenView? = null

	private var heroItems: List<BaseItemDto> = emptyList()
	private var heroIndex = 0
	private var heroRotationJob: Job? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val view = inflater.inflate(R.layout.fragment_home, container, false)

		titleView = view.findViewById(R.id.title)
		logoView = view.findViewById(R.id.logo)
		infoRowView = view.findViewById(R.id.infoRow)
		summaryView = view.findViewById(R.id.summary)
		backgroundImage = view.findViewById(R.id.backgroundImage)
		snowfallView = view.findViewById(R.id.snowfallView)
		petalfallView = view.findViewById(R.id.petalfallView)
		leaffallView = view.findViewById(R.id.leaffallView)
		summerView = view.findViewById(R.id.summerView)
		halloweenView = view.findViewById(R.id.halloweenView)

		// trailerWebView exists in layout but is no longer used
		view.findViewById<View>(R.id.trailerWebView)?.isVisible = false

		setupNavbar(view)

		return view
	}

	private fun setupNavbar(view: View) {
		val navbarPosition = userPreferences[UserPreferences.navbarPosition] ?: NavbarPosition.TOP

		when (navbarPosition) {
			NavbarPosition.LEFT -> {
				val toolbarContainer = view.findViewById<FrameLayout>(R.id.toolbar_actions)
				toolbarContainer.isVisible = false

				val sidebarContainer = view.findViewById<FrameLayout>(R.id.left_sidebar)
				sidebarContainer.isVisible = true

				val sidebarView = view.findViewById<ComposeView>(R.id.sidebar)
				sidebarView.setContent {
					LeftSidebarNavigation(
						activeButton = NavbarActiveButton.Home
					)
				}
			}
			NavbarPosition.TOP -> {
				val sidebarContainer = view.findViewById<FrameLayout>(R.id.left_sidebar)
				sidebarContainer.isVisible = false

				val toolbarContainer = view.findViewById<FrameLayout>(R.id.toolbar_actions)
				toolbarContainer.isVisible = true

				val toolbarView = view.findViewById<ComposeView>(R.id.toolbar)
				toolbarView.setContent {
					Navbar(
						activeButton = NavbarActiveButton.Home
					)
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		setupSeasonalSurprise()

		settingsViewModel.settingsClosedCounter
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach {
				setupSeasonalSurprise()
				view.let { setupNavbar(it) }
			}
			.launchIn(lifecycleScope)

		rowsFragment = childFragmentManager.findFragmentById(R.id.rowsFragment) as? HomeRowsFragment

		rowsFragment?.selectedItemStateFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { state ->
				titleView?.text = state.title
				summaryView?.text = state.summary
				infoRowView?.setItem(state.baseItem)
			}
			?.launchIn(lifecycleScope)

		// When user scrolls away from row 0, hide hero spotlight and show item info
		rowsFragment?.selectedPositionFlow
			?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			?.onEach { position ->
				updateHeroVisibility(position)
			}
			?.launchIn(lifecycleScope)

		// Fetch Tentacle hero items
		loadHeroItems()
	}

	private fun loadHeroItems() {
		lifecycleScope.launch {
			try {
				val items = kotlinx.coroutines.withTimeoutOrNull(5000L) {
					tentacleRepository.getHeroItems()
				} ?: emptyList()

				heroItems = items
				heroIndex = 0

				if (items.isNotEmpty()) {
					showHeroItem(items[0])
					startHeroRotation()
				} else {
					Timber.d("No Tentacle hero items — hiding spotlight")
					backgroundImage?.isVisible = false
					logoView?.isVisible = false
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load Tentacle hero items")
				backgroundImage?.isVisible = false
				logoView?.isVisible = false
			}
		}
	}

	private fun showHeroItem(item: BaseItemDto) {
		// Backdrop
		val backdropTag = item.backdropImageTags?.firstOrNull()
		if (backdropTag != null) {
			val backdropUrl = api.imageApi.getItemImageUrl(
				itemId = item.id,
				imageType = ImageType.BACKDROP,
				tag = backdropTag,
				maxWidth = 1920,
			)
			backgroundImage?.isVisible = true
			backgroundImage?.load(backdropUrl) {
				crossfade(400)
			}
		} else {
			backgroundImage?.isVisible = false
		}

		// Logo
		val logoTag = item.imageTags?.get(ImageType.LOGO)
		if (logoTag != null) {
			val logoUrl = api.imageApi.getItemImageUrl(
				itemId = item.id,
				imageType = ImageType.LOGO,
				tag = logoTag,
				maxWidth = 800,
			)
			logoView?.isVisible = true
			logoView?.load(logoUrl) {
				crossfade(300)
			}
		} else {
			logoView?.isVisible = false
		}
	}

	private fun startHeroRotation() {
		heroRotationJob?.cancel()
		if (heroItems.size <= 1) return

		heroRotationJob = lifecycleScope.launch {
			while (true) {
				delay(8000L)
				heroIndex = (heroIndex + 1) % heroItems.size
				showHeroItem(heroItems[heroIndex])
			}
		}
	}

	private fun updateHeroVisibility(selectedPosition: Int) {
		if (heroItems.isEmpty()) return

		if (selectedPosition == 0) {
			// On first row — show hero backdrop/logo, hide text info
			showHeroItem(heroItems[heroIndex])
			titleView?.isVisible = false
			infoRowView?.isVisible = false
			summaryView?.isVisible = false
		} else {
			// Scrolled past first row — hide hero, show item text info
			backgroundImage?.isVisible = false
			logoView?.isVisible = false
			titleView?.isVisible = true
			infoRowView?.isVisible = true
			summaryView?.isVisible = true
		}
	}

	private fun setupSeasonalSurprise() {
		val selection = userPreferences[UserPreferences.seasonalSurprise]

		snowfallView?.isVisible = false
		snowfallView?.stopSnowing()
		petalfallView?.isVisible = false
		petalfallView?.stopFalling()
		leaffallView?.isVisible = false
		leaffallView?.stopFalling()
		summerView?.isVisible = false
		summerView?.stopEffect()
		halloweenView?.isVisible = false
		halloweenView?.stopEffect()

		when (selection) {
			"winter" -> {
				snowfallView?.isVisible = true
				snowfallView?.startSnowing()
			}
			"spring" -> {
				petalfallView?.isVisible = true
				petalfallView?.startFalling()
			}
			"summer" -> {
				summerView?.isVisible = true
				summerView?.startEffect()
			}
			"halloween" -> {
				halloweenView?.isVisible = true
				halloweenView?.startEffect()
			}
			"fall" -> {
				leaffallView?.isVisible = true
				leaffallView?.startFalling()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		loadHeroItems()
	}

	override fun onPause() {
		super.onPause()
		heroRotationJob?.cancel()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		heroRotationJob?.cancel()
		snowfallView?.stopSnowing()
		petalfallView?.stopFalling()
		leaffallView?.stopFalling()
		summerView?.stopEffect()
		halloweenView?.stopEffect()
		titleView = null
		logoView = null
		summaryView = null
		infoRowView = null
		backgroundImage = null
		rowsFragment = null
		snowfallView = null
		petalfallView = null
		leaffallView = null
		summerView = null
		halloweenView = null
	}
}
