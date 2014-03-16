#
# Select a servers file in dir $BIOMINE_TV_CONFIG and set its path into
# env variable ABBOE_SERVERS_FILE
# 
# Usage:
#   - ensure BIOMINE_TV_CONFIG set or $BIOMINE_TV_ROOT/config exists
#   - source this file (abboe-select-servers.sh)
#   - call function abboe-select-servers
#
# Requires: whiptail
#


# set $BIOMINE_TV_CONFIG; return 1 on failure to set
function resolve_config_dir() {
    if [ "BIOMINE_TV_CONFIG" != "" ] && [ -d "$BIOMINE_TV_CONFIG" ]; then
	# no action needed
	return 0
    fi	
    
    if [ "$BIOMINE_TV_ROOT" == "" ]; then
	echo "BIOMINE_TV_ROOT not defined" >&2
	return 1
    fi

    BIOMINE_TV_CONFIG="$BIOMINE_TV_ROOT/config"
  
    if ! [ -d "$BIOMINE_TV_CONFIG" ]; then
	echo "No such BIOMINE_TV_CONFIG dir: $BIOMINE_TV_CONFIG" >&2
	return 1
    fi
  
    export BIOMINE_TV_CONFIG
}

function abboe-list-server-files() {
    resolve_config_dir || exit 1

    local SERVERLIST_FILES_FILE
    local DEFAULT_SERVERLIST_FILES_FILE="$BIOMINE_TV_CONFIG/servers_files.txt"
    if [ -e $DEFAULT_SERVERLIST_FILES_FILE ]; then
	SERVERLIST_FILES_FILE="$DEFAULT_SERVERLIST_FILES_FILE"
    else
	SERVERLIST_FILES_FILE=$(mktemp /tmp/select-servers.serverlist.XXXXXX)
	ls "$BIOMINE_TV_CONFIG" \
	| grep 'servers.json$' \
        > $SERVERLIST_FILES_FILE    
  fi

  cat "$SERVERLIST_FILES_FILE" | awk '{print NR " " $1}'
}

function abboe-select-servers() {
    resolve_config_dir || exit 1

    local SELFILE=$(mktemp /tmp/select-servers.selection.XXXXXX)
    eval `resize`;
    local SERVERLIST_FILES_FILE=$(mktemp /tmp/select-servers.serverlist.XXXXXX)
    abboe-list-server-files > $SERVERLIST_FILES_FILE      

    cat $SERVERLIST_FILES_FILE \
      | tee serverlist_files.tmp \
      | xargs whiptail \
        --title "ABBOE server selection" \
	--menu "Choose your servers" $LINES $COLUMNS $(( $LINES - 8 )) \
    2> $SELFILE

    if (( $? != 0 )); then
	echo "No selection"
	return 0
    fi

    local SELIND=$(cat $SELFILE)
    local SERVERFILE=$(abboe-list-server-files | awk -v SELIND=$SELIND '$1 == SELIND {print $2}')
    echo "Selecting servers file: $BIOMINE_TV_CONFIG/$SERVERFILE"

    if [ -e "$SERVERFILE" ]; then
	echo "No such file: $SERVERFILE"
	return 1
    fi

    export ABBOE_SERVERS_FILE=$BIOMINE_TV_CONFIG/$SERVERFILE
}

