#!/bin/bash

set -euo pipefail

find_root() {
	CORE=$(git rev-parse --show-toplevel)/app/core

	if [[ ! -d $CORE ]]; then
		echo "$0: unable to find app/core directory" >&2
		exit 1
	fi
}

find_sim_triple() {
	case $(uname -m) in
	    x86_64)
	    	echo -n x86_64-apple-ios
		;;
	    aarch64 | arm64)
	    	echo -n aarch64-apple-ios-sim
		;;
	    *)
		echo "Unsupported architecture: $(uname -m)" >&2
		exit 1
		;;
	esac
}

build() {
	local target=$1

	# rustup only detects rust-toolchain.toml in the cwd
	(
		cd "$CORE"
		rustup target add $target
		cargo build \
			--lib \
			--package=ffi \
			--release \
			--target=$target \
			--locked
	)
}

lib() {
	local framework_root=$1
	shift
	local targets=$*

	mkdir -p $framework_root
	lipo \
		-create $(printf "$BUILD_TARGET/%s/release/libcore.a\n" $targets) \
		-output $framework_root/$FRAMEWORK
}

bindgen() {
	local target=$1
	local swift_root=$2

	(
		cd "$CORE"
		cargo run \
			--bin uniffi-bindgen \
			generate \
			--library $BUILD_TARGET/$target/release/libcore.dylib \
			--language swift \
			--out-dir $swift_root
	)
}

header() {
	local framework_headers=$1/Headers
	mkdir -p $framework_headers

	local swift_root=$2
	cp $swift_root/$FRAMEWORK.h $framework_headers
}

modulemap() {
	local framework_modules=$1/Modules
	mkdir -p $framework_modules

	local swift_root=$2
	sed \
		-e "s/^module/framework module/" \
		$swift_root/$FRAMEWORK.modulemap > $framework_modules/module.modulemap
}

framework() {
	local framework_root=$RUST_BUILD_DIRECTORY/ios/$1/$FRAMEWORK.framework
	shift
	local targets=$*

	for target in $targets; do
		build $target
	done

	lib $framework_root $targets
	local swift_root=$RUST_BUILD_DIRECTORY/uniffi/swift
	bindgen $target $swift_root
	header $framework_root $swift_root
	modulemap $framework_root $swift_root
}

xcframework() {
	local output=$RUST_BUILD_DIRECTORY/ios/$FRAMEWORK.xcframework

	[ -d "$output" ] && rm -rf "$output"
	xcodebuild \
		-create-xcframework \
		$(printf -- "-framework $RUST_BUILD_DIRECTORY/ios/%s/$FRAMEWORK.framework\n" $*) \
		-output $output
}

find_root
RUST_BUILD_DIRECTORY=$CORE/_build/rust
BUILD_TARGET=$RUST_BUILD_DIRECTORY/target
FRAMEWORK=coreFFI

framework ios aarch64-apple-ios
framework ios-sim $(find_sim_triple)
xcframework ios ios-sim
