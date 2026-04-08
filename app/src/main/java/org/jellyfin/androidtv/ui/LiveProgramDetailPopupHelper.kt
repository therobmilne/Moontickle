package org.jellyfin.androidtv.ui

import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.util.getActivity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import org.jellyfin.sdk.model.api.TimerInfoDto
import org.koin.android.ext.android.inject
import java.util.UUID

fun BaseItemDto.copyWithTimerId(
	timerId: String?,
) = copy(
	timerId = timerId,
)

fun BaseItemDto.copyWithSeriesTimerId(
	seriesTimerId: String?,
) = copy(
	seriesTimerId = seriesTimerId,
)

fun LiveProgramDetailPopup.cancelTimer(
	timerId: String,
	callback: () -> Unit,
) {
	val api by mContext.getActivity()!!.inject<ApiClient>()

	lifecycle.coroutineScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.cancelTimer(timerId)
			}
		}.onSuccess {
			callback()
		}
	}
}

fun LiveProgramDetailPopup.cancelSeriesTimer(
	seriesTimerId: String,
	callback: () -> Unit,
) {
	val api by mContext.getActivity()!!.inject<ApiClient>()

	lifecycle.coroutineScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.cancelSeriesTimer(seriesTimerId)
			}
		}.onSuccess {
			callback()
		}
	}
}

fun SeriesTimerInfoDto.asTimerInfoDto() = TimerInfoDto(
	id = id,
	type = type,
	serverId = serverId,
	externalId = externalId,
	channelId = channelId,
	externalChannelId = externalChannelId,
	channelName = channelName,
	channelPrimaryImageTag = channelPrimaryImageTag,
	programId = programId,
	externalProgramId = externalProgramId,
	name = name,
	overview = overview,
	startDate = startDate,
	endDate = endDate,
	serviceName = serviceName,
	priority = priority,
	prePaddingSeconds = prePaddingSeconds,
	postPaddingSeconds = postPaddingSeconds,
	isPrePaddingRequired = isPrePaddingRequired,
	parentBackdropItemId = parentBackdropItemId,
	parentBackdropImageTags = parentBackdropImageTags,
	isPostPaddingRequired = isPostPaddingRequired,
	keepUntil = keepUntil,
)

fun LiveProgramDetailPopup.recordProgram(
	programId: UUID,
	callback: (program: BaseItemDto) -> Unit,
) {
	val api by mContext.getActivity()!!.inject<ApiClient>()

	lifecycle.coroutineScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				val seriesTimer by api.liveTvApi.getDefaultTimer(programId.toString())
				val timer = seriesTimer.asTimerInfoDto()
				api.liveTvApi.createTimer(timer)
				api.liveTvApi.getProgram(programId.toString()).content
			}
		}.onSuccess { program ->
			callback(program)
		}.onFailure { error ->
			timber.log.Timber.e(error, "Failed to record program $programId")
			val errorMsg = when {
				error.message?.contains("500") == true && error.message?.contains("path") == true ->
					"Recording failed. Configure a recording path in Jellyfin Dashboard > Live TV > DVR."
				error.message?.contains("403") == true ->
					"Recording failed: No recording permission."
				error.message?.contains("UNIQUE") == true ->
					"Timer already exists for this program."
				else -> "Recording failed: ${error.message ?: "Unknown error"}"
			}
			android.widget.Toast.makeText(mContext, errorMsg, android.widget.Toast.LENGTH_LONG).show()
		}
	}
}

fun LiveProgramDetailPopup.recordSeries(
	programId: UUID,
	callback: (program: BaseItemDto) -> Unit,
) {
	val api by mContext.getActivity()!!.inject<ApiClient>()

	lifecycle.coroutineScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				val timer by api.liveTvApi.getDefaultTimer(programId.toString())
				api.liveTvApi.createSeriesTimer(timer)
				api.liveTvApi.getProgram(programId.toString()).content
			}
		}.onSuccess { program ->
			callback(program)
		}.onFailure { error ->
			timber.log.Timber.e(error, "Failed to record series for program $programId")
			val errorMsg = when {
				error.message?.contains("500") == true && error.message?.contains("path") == true ->
					"Series recording failed. Configure a recording path in Jellyfin Dashboard > Live TV > DVR."
				error.message?.contains("403") == true ->
					"Series recording failed: No recording permission."
				else -> "Series recording failed: ${error.message ?: "Unknown error"}"
			}
			android.widget.Toast.makeText(mContext, errorMsg, android.widget.Toast.LENGTH_LONG).show()
		}
	}
}

fun LiveProgramDetailPopup.getSeriesTimer(
	seriesTimerId: String,
	callback: (seriesTimer: SeriesTimerInfoDto) -> Unit,
) {
	val api by mContext.getActivity()!!.inject<ApiClient>()

	lifecycle.coroutineScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getSeriesTimer(seriesTimerId).content
			}
		}.onSuccess { seriesTimer ->
			callback(seriesTimer)
		}
	}
}

fun LiveProgramDetailPopup.toggleFavorite(
	item: BaseItemDto,
	callback: (item: BaseItemDto) -> Unit,
) {
	val itemMutationRepository by mContext.getActivity()!!.inject<ItemMutationRepository>()

	lifecycle.coroutineScope.launch {
		runCatching {
			val userData = itemMutationRepository.setFavorite(
				item = item.id,
				favorite = !(item.userData?.isFavorite ?: false)
			)

			item.copy(userData = userData)
		}.onSuccess { item ->
			callback(item)
		}
	}
}
