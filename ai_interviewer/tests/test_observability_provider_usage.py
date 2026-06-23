import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.observability.provider_usage import normalize_provider_usage


def test_deepseek_prompt_cache_tokens_are_normalized():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 25,
        "total_tokens": 125,
        "prompt_cache_hit_tokens": 60,
        "prompt_cache_miss_tokens": 40,
    }

    result = normalize_provider_usage("deepseek", usage)

    assert result.prompt_tokens == 100
    assert result.completion_tokens == 25
    assert result.total_tokens == 125
    assert result.prompt_cache_hit_tokens == 60
    assert result.prompt_cache_miss_tokens == 40
    assert result.prompt_cache_hit_rate == 0.6
    assert result.cache_reported_by_provider is True
    assert result.token_source == "provider"
    assert result.raw_usage == usage


def test_openai_cached_tokens_are_normalized():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 15,
        "total_tokens": 115,
        "prompt_tokens_details": {"cached_tokens": 35},
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_tokens == 100
    assert result.completion_tokens == 15
    assert result.total_tokens == 115
    assert result.prompt_cache_hit_tokens == 35
    assert result.prompt_cache_miss_tokens == 65
    assert result.prompt_cache_hit_rate == 0.35
    assert result.cache_reported_by_provider is True
    assert result.token_source == "provider"
    assert result.raw_usage == usage


def test_invalid_openai_cached_tokens_are_ignored():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 15,
        "total_tokens": 115,
        "prompt_tokens_details": {"cached_tokens": "35"},
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_tokens == 100
    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.cache_reported_by_provider is False


def test_cached_tokens_greater_than_prompt_does_not_create_negative_miss():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 15,
        "total_tokens": 115,
        "prompt_tokens_details": {"cached_tokens": 120},
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_cache_hit_tokens == 120
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.cache_reported_by_provider is True


def test_negative_openai_cached_tokens_are_ignored():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 15,
        "total_tokens": 115,
        "prompt_tokens_details": {"cached_tokens": -5},
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.cache_reported_by_provider is False


def test_invalid_deepseek_cache_tokens_are_ignored():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 25,
        "total_tokens": 125,
        "prompt_cache_hit_tokens": "60",
        "prompt_cache_miss_tokens": True,
    }

    result = normalize_provider_usage("deepseek", usage)

    assert result.prompt_tokens == 100
    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.cache_reported_by_provider is False


def test_negative_deepseek_cache_tokens_are_ignored():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 25,
        "total_tokens": 125,
        "prompt_cache_hit_tokens": -5,
        "prompt_cache_miss_tokens": -10,
    }

    result = normalize_provider_usage("deepseek", usage)

    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.cache_reported_by_provider is False


def test_invalid_provider_token_fields_are_ignored():
    usage = {
        "prompt_tokens": "100",
        "completion_tokens": True,
        "total_tokens": 115.5,
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_tokens is None
    assert result.completion_tokens is None
    assert result.total_tokens is None


def test_negative_provider_token_fields_are_ignored():
    usage = {
        "prompt_tokens": -100,
        "completion_tokens": -25,
        "total_tokens": -125,
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_tokens is None
    assert result.completion_tokens is None
    assert result.total_tokens is None


def test_unreported_cache_fields_are_excluded_from_cache_metrics():
    result = normalize_provider_usage(
        "azure_openai",
        {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
    )

    assert result.prompt_tokens == 10
    assert result.completion_tokens == 5
    assert result.total_tokens == 15
    assert result.cache_reported_by_provider is False
    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.token_source == "provider"


def test_missing_usage_becomes_estimated_source_without_cache_metrics():
    result = normalize_provider_usage(
        "unknown",
        None,
        estimated_prompt_tokens=12,
        estimated_completion_tokens=8,
    )

    assert result.prompt_tokens == 12
    assert result.completion_tokens == 8
    assert result.total_tokens == 20
    assert result.token_source == "estimated"
    assert result.cache_reported_by_provider is False
    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None
    assert result.raw_usage == {}


def test_estimated_total_is_unknown_when_one_estimate_is_missing():
    result = normalize_provider_usage(
        "unknown",
        None,
        estimated_prompt_tokens=12,
    )

    assert result.prompt_tokens == 12
    assert result.completion_tokens is None
    assert result.total_tokens is None
    assert result.token_source == "estimated"


def test_negative_estimated_tokens_are_ignored():
    result = normalize_provider_usage(
        "unknown",
        None,
        estimated_prompt_tokens=-12,
        estimated_completion_tokens=-8,
    )

    assert result.prompt_tokens is None
    assert result.completion_tokens is None
    assert result.total_tokens is None
    assert result.token_source == "estimated"


def test_raw_usage_is_copied_to_avoid_mutating_normalized_records():
    usage = {"prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5}

    result = normalize_provider_usage("deepseek", usage)
    usage["prompt_tokens"] = 99

    assert result.raw_usage["prompt_tokens"] == 3
