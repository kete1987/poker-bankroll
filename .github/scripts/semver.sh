# Semantic version helpers shared by the release scripts (source this file).
# Grammar from https://semver.org without build metadata: MAJOR.MINOR.PATCH[-PRERELEASE], no
# leading zeros in numbers, no empty pre-release identifiers.

_semver_num='(0|[1-9][0-9]*)'
_semver_pre_id='(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'

# True for 1.2.3 and 1.2.3-rc.1; false for 01.2.3, 1.2, 1.2.3-rc..1, v1.2.3...
is_version() {
  [[ $1 =~ ^$_semver_num\.$_semver_num\.$_semver_num(-$_semver_pre_id(\.$_semver_pre_id)*)?$ ]]
}

# True only for stable versions: 1.2.3, not 1.2.3-rc.1.
is_stable_version() {
  [[ $1 =~ ^$_semver_num\.$_semver_num\.$_semver_num$ ]]
}
