extends TestCase

var score: ScoreTracker


func before_each() -> void:
	score = ScoreTracker.new()
	score.reset(100.0)


func test_progress_counts_whole_tiles_from_start() -> void:
	score.update_progress(100.0 + GameConst.TILE * 3.9)
	assert_eq(score.tiles, 3)
	assert_eq(score.get_score(), 30)


func test_progress_only_moves_forward() -> void:
	score.update_progress(100.0 + GameConst.TILE * 5)
	score.update_progress(100.0 + GameConst.TILE * 2)
	assert_eq(score.tiles, 5)


func test_shards_add_points() -> void:
	score.add_shard()
	score.add_shard()
	assert_eq(score.get_score(), 2 * ScoreTracker.POINTS_PER_SHARD)


func test_snapshot_restore_rolls_back() -> void:
	score.update_progress(100.0 + GameConst.TILE * 10)
	score.add_shard()
	var snap := score.snapshot()
	score.update_progress(100.0 + GameConst.TILE * 20)
	score.add_shard()
	score.restore(snap)
	assert_eq(score.tiles, 10)
	assert_eq(score.shards, 1)
	assert_eq(score.get_score(), 100 + ScoreTracker.POINTS_PER_SHARD)


func test_changed_signal_reports_score_and_shards() -> void:
	var seen: Array = []
	score.changed.connect(func(s: int, n: int) -> void: seen.append([s, n]))
	score.add_shard()
	assert_eq(seen, [[ScoreTracker.POINTS_PER_SHARD, 1]])
