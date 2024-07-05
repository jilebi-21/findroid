package dev.jdtech.jellyfin.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.AppPreferences
import dev.jdtech.jellyfin.adapters.PersonListAdapter
import dev.jdtech.jellyfin.adapters.ViewItemListAdapter
import dev.jdtech.jellyfin.bindCardItemImage
import dev.jdtech.jellyfin.bindItemBackdropImage
import dev.jdtech.jellyfin.databinding.FragmentShowBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import dev.jdtech.jellyfin.viewmodels.PlayerItemsEvent
import dev.jdtech.jellyfin.viewmodels.PlayerViewModel
import dev.jdtech.jellyfin.viewmodels.ShowEvent
import dev.jdtech.jellyfin.viewmodels.ShowViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class ShowFragment : Fragment() {

    private lateinit var binding: FragmentShowBinding
    private val viewModel: ShowViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val args: ShowFragmentArgs by navArgs()

    private lateinit var errorDialog: ErrorDialogFragment

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentShowBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        when (uiState) {
                            is ShowViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                            is ShowViewModel.UiState.Loading -> bindUiStateLoading()
                            is ShowViewModel.UiState.Error -> bindUiStateError(uiState)
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is ShowEvent.NavigateBack -> findNavController().navigateUp()
                        }
                    }
                }

                launch {
                    playerViewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerItemsEvent.PlayerItemsReady -> bindPlayerItems(event.items)
                            is PlayerItemsEvent.PlayerItemsError -> bindPlayerItemsError(event.error)
                        }
                    }
                }
            }
        }

        // TODO make download button work for shows
        binding.itemActions.downloadButton.visibility = View.GONE
        binding.itemActions.progressDownload.visibility = View.GONE

        binding.errorLayout.errorRetryButton.setOnClickListener {
            viewModel.loadData(args.itemId, args.offline)
        }

        binding.itemActions.trailerButton.setOnClickListener {
            viewModel.item.trailer.let { trailerUri ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(trailerUri),
                )
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.nextUp.setOnClickListener {
            navigateToEpisodeBottomSheetFragment(viewModel.nextUp!!)
        }

        binding.seasonsRecyclerView.adapter =
            ViewItemListAdapter(
                { season ->
                    if (season is FindroidSeason) navigateToSeasonFragment(season)
                },
                fixedWidth = true,
            )
        binding.peopleRecyclerView.adapter = PersonListAdapter { person ->
            navigateToPersonDetail(person.id)
        }

        binding.itemActions.playButton.setOnClickListener {
            binding.itemActions.playButton.isEnabled = false
            binding.itemActions.progressPlay.isVisible = true
            playerViewModel.loadPlayerItems(viewModel.item)
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(parentFragmentManager, ErrorDialogFragment.TAG)
        }

        binding.itemActions.checkButton.setOnClickListener {
            viewModel.togglePlayed()
        }

        binding.itemActions.favoriteButton.setOnClickListener {
            viewModel.toggleFavorite()
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.loadData(args.itemId, args.offline)
    }

    private fun bindUiStateNormal(uiState: ShowViewModel.UiState.Normal) {
        uiState.apply {
            val downloaded = item.isDownloaded()
            val canDownload =
                item.canDownload && item.sources.any { it.type == FindroidSourceType.REMOTE }

            binding.originalTitle.isVisible = item.originalTitle != item.name
            if (item.trailer != null) {
                binding.itemActions.trailerButton.isVisible = true
            }
            binding.actors.isVisible = actors.isNotEmpty()

            // TODO currently the sources of a show is always empty, we need a way to check if sources are available
            binding.itemActions.playButton.isEnabled = item.canPlay
            binding.itemActions.checkButton.isEnabled = true
            binding.itemActions.favoriteButton.isEnabled = true

            bindCheckButtonState(item.played)

            bindFavoriteButtonState(item.favorite)

            when (canDownload) {
                true -> {
                    binding.itemActions.downloadButton.isVisible = true
                    binding.itemActions.downloadButton.isEnabled = !downloaded

                    if (downloaded) {
                        binding.itemActions.downloadButton.setIconTintResource(
                            android.R.color.holo_red_light,
                        )
                    }
                }

                false -> {
                    binding.itemActions.downloadButton.isVisible = false
                }
            }

            binding.apply {
                name.text = item.name
                originalTitle.text = item.originalTitle

                dateString.isNotEmpty().also {
                    metadata.year.text = dateString
                    metadata.yearDot.isVisible = it
                    metadata.year.isVisible = it
                }

                runTime.isNotEmpty().also {
                    metadata.playtime.text = runTime
                    metadata.playtimeDot.isVisible = it
                    metadata.playtime.isVisible = it
                }

                item.officialRating.isNullOrEmpty().also {
                    metadata.officialRating.text = item.officialRating
                    metadata.officialRatingContainer.isVisible = !it
                    metadata.officialRatingDot.isVisible = !it
                }

                item.communityRating.also {
                    metadata.communityRating.text = it.toString()
                    metadata.communityRating.isVisible = it !== null

                    //hide if any extra dot separators
                    if (it == null) {
                        if (metadata.officialRatingDot.isVisible)
                            metadata.officialRatingDot.isVisible = false
                        else if (metadata.playtimeDot.isVisible) {
                            metadata.playtimeDot.isVisible = false
                        } else if (metadata.yearDot.isVisible) {
                            metadata.yearDot.isVisible = false
                        }
                    }
                }
            }

            binding.info.description.text = fromHtml(item.overview, 0)
            binding.info.director.text = director?.name
            binding.info.directorGroup.isVisible = director != null
            binding.info.writers.text = writersString
            binding.info.writersGroup.isVisible = writers.isNotEmpty()

            binding.nextUpLayout.isVisible = nextUp != null
            if (nextUp?.indexNumberEnd == null) {
                binding.nextUpName.text = getString(
                    CoreR.string.episode_name_extended,
                    nextUp?.parentIndexNumber,
                    nextUp?.indexNumber,
                    nextUp?.name,
                )
            } else {
                binding.nextUpName.text = getString(
                    CoreR.string.episode_name_extended_with_end,
                    nextUp?.parentIndexNumber,
                    nextUp?.indexNumber,
                    nextUp?.indexNumberEnd,
                    nextUp?.name,
                )
            }

            binding.seasonsLayout.isVisible = seasons.isNotEmpty()
            val seasonsAdapter = binding.seasonsRecyclerView.adapter as ViewItemListAdapter
            seasonsAdapter.submitList(seasons)
            val actorsAdapter = binding.peopleRecyclerView.adapter as PersonListAdapter
            actorsAdapter.submitList(actors)
            bindItemBackdropImage(binding.itemBanner, item)
            if (nextUp != null) bindCardItemImage(binding.nextUpImage, nextUp!!)
        }
        binding.loadingIndicator.isVisible = false
        binding.mediaInfoScrollview.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateError(uiState: ShowViewModel.UiState.Error) {
        errorDialog = ErrorDialogFragment.newInstance(uiState.error)
        binding.loadingIndicator.isVisible = false
        binding.mediaInfoScrollview.isVisible = false
        binding.errorLayout.errorPanel.isVisible = true
        checkIfLoginRequired(uiState.error.message)
    }

    private fun bindCheckButtonState(played: Boolean) {
        binding.itemActions.checkButton.isChecked = played
    }

    private fun bindFavoriteButtonState(favorite: Boolean) {
        val favoriteDrawable =
            if (favorite) CoreR.drawable.ic_heart_filled
            else CoreR.drawable.ic_heart
        binding.itemActions.favoriteButton.setIconResource(favoriteDrawable)
        binding.itemActions.favoriteButton.isChecked = favorite
    }

    private fun bindPlayerItems(items: List<PlayerItem>) {
        navigateToPlayerActivity(items.toTypedArray())
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun bindPlayerItemsError(error: Exception) {
        Timber.e(error.message)
        binding.playerItemsError.visibility = View.VISIBLE
        playButtonNormal()
        binding.playerItemsErrorDetails.setOnClickListener {
            ErrorDialogFragment.newInstance(error)
                .show(parentFragmentManager, ErrorDialogFragment.TAG)
        }
    }

    private fun playButtonNormal() {
        binding.itemActions.playButton.isEnabled = true
        binding.itemActions.progressPlay.visibility = View.INVISIBLE
    }

    private fun navigateToEpisodeBottomSheetFragment(episode: FindroidItem) {
        findNavController().navigate(
            ShowFragmentDirections.actionShowFragmentToEpisodeBottomSheetFragment(
                episode.id,
            ),
        )
    }

    private fun navigateToSeasonFragment(season: FindroidSeason) {
        findNavController().navigate(
            ShowFragmentDirections.actionShowFragmentToSeasonFragment(
                season.seriesId,
                season.id,
                season.seriesName,
                season.name,
                args.offline,
            ),
        )
    }

    private fun navigateToPlayerActivity(
        playerItems: Array<PlayerItem>,
    ) {
        findNavController().navigate(
            ShowFragmentDirections.actionShowFragmentToPlayerActivity(
                playerItems,
            ),
        )
    }

    private fun navigateToPersonDetail(personId: UUID) {
        findNavController().navigate(
            ShowFragmentDirections.actionShowFragmentToPersonDetailFragment(personId),
        )
    }
}
