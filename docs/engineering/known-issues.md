# Known issues

Last checked: 2026-11-17

These items were observed during the MISEBASE validation runs for the archive-category and timer changes. They are outside the requested feature scope and do not block the signed APK build.

## Repository-wide CI quality gate

The full `ci` workflow still reports Kotlin formatting violations in existing files outside the current feature changes, including:

- `app/src/main/java/com/culinary/cooking/CookingPlan.kt`
- `app/src/main/java/com/culinary/di/AppModule.kt`
- `app/src/main/java/com/culinary/navigation/CulinaryNavHost.kt`
- `core/data/src/main/java/com/culinary/core/data/di/InventoryArchiveUseCaseModule.kt`
- `core/data/src/main/java/com/culinary/core/data/mapper/RecipeMapper.kt`
- `core/data/src/main/java/com/culinary/core/data/mapper/SauceMapper.kt`
- `core/data/src/main/java/com/culinary/core/data/repository/InventoryArchiveRepositoryImpl.kt`
- `core/data/src/main/java/com/culinary/core/data/repository/RecipeRepositoryImpl.kt`
- `core/data/src/main/java/com/culinary/core/data/repository/SauceRepositoryImpl.kt`

The repository-wide gate also reports formatting violations in pre-existing sections of `ArchiveCategoryRepositoryImpl.kt`; the category deletion and sibling-reorder logic added for this task is separate from those locations.

## Existing broader test-suite failures

Earlier unfiltered test runs exposed unrelated failures:

- Some `feature:recipes` test code does not supply the current `onDismissError` parameter.
- `core:data` test fake `InMemoryRecipeRepository` does not implement the current `archive` contract.
- An unfiltered app test run hit memory exhaustion in multiple `NavigationShellTest` cases.

The APK workflow therefore runs the focused `CookingViewModelTest` suite for the timer-lock behavior, then compiles and builds the full app. That focused suite passed on the final build run.

## Operational status

No user-facing runtime defect was confirmed from the above CI findings. They remain recorded for a separate maintenance pass rather than expanding this feature change into unrelated modules.
