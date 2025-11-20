import argparse
import re
import sys
import win32com.client
from datetime import datetime


# Simple logging helper – all logs go to STDOUT and are ignored by the Java parser
def log(level: str, message: str) -> None:
    print(f"LOG:{level}:{message}")


def find_folder_recursive(base_folder, target_name: str):
    """
    Search all subfolders under base_folder for a folder whose Name matches target_name (case-insensitive).
    Returns the first match, or None if not found.
    """
    target_name_lower = target_name.lower()
    if base_folder.Name.lower() == target_name_lower:
        return base_folder
    for subfolder in base_folder.Folders:
        found = find_folder_recursive(subfolder, target_name)
        if found:
            return found
    return None


def after_keyword(line: str, kw: str) -> str | None:
    """Return text after kw in line (case-insensitive), trimming separators."""
    idx = line.lower().find(kw.lower())
    if idx == -1:
        return None
    tail = line[idx + len(kw):]
    return tail.lstrip(" :\t").strip()


def extract_failed_app_name(line: str, fail_keyword: str) -> str | None:
    """
    Given a NOTINSTALLED line, try to return ONLY the app name part.
    Works with lines like:
        "NOTINSTALLED FlexNet Inventory Agent 23.01.1199 _GUID Application 11/20/2025 ..."
    """
    # Remove the fail keyword itself
    cleaned = re.sub(re.escape(fail_keyword), "", line, flags=re.I).strip()
    cleaned = re.sub(r"^[\s:=-]+", "", cleaned).strip()

    if not cleaned:
        return None

    # Cut off at the first GUID (with or without leading underscore / braces)
    cleaned = re.split(
        r"[_{]?[0-9a-fA-F]{8}-"
        r"[0-9a-fA-F]{4}-"
        r"[0-9a-fA-F]{4}-"
        r"[0-9a-fA-F]{4}-"
        r"[0-9a-fA-F]{12}",
        cleaned,
        maxsplit=1
    )[0].strip()

    # Cut off at common trailing tokens
    cleaned = re.split(r"\b(Application|Package|Program)\b", cleaned, maxsplit=1)[0].strip()

    # Cut off at a date if still present
    cleaned = re.split(r"\b\d{1,2}/\d{1,2}/\d{4}\b", cleaned, maxsplit=1)[0].strip()

    return cleaned or None


def parse_email_body(body: str, keywords: dict) -> dict:
    """
    Extract serial number, reimage time and failed installs details from the message body.
    Returns failed_installs as a semicolon-separated list of APP NAMES only.
    """
    data = {
        "serial_number": "N/A",
        "reimage_time": "N/A",
        "failed_installs": "0",
    }

    lines = body.splitlines()
    summary_count = None

    # Look for summary count (don't break — we still want names if they exist)
    for line in lines:
        lower = line.lower()
        if "the following" in lower and "items failed to install" in lower:
            m = re.search(r"(\d+)", line)
            if m:
                summary_count = m.group(1)

    # Collect failed app *names*
    fail_keyword = keywords["failed"]
    failed_apps = []
    for line in lines:
        if fail_keyword.lower() in line.lower():
            name = extract_failed_app_name(line, fail_keyword)
            if name:
                failed_apps.append(name)

    # Deduplicate while preserving order
    seen = set()
    uniq_apps = []
    for a in failed_apps:
        if a not in seen:
            seen.add(a)
            uniq_apps.append(a)

    if uniq_apps:
        data["failed_installs"] = "; ".join(uniq_apps)
    elif summary_count is not None:
        data["failed_installs"] = f"{summary_count} items (from summary)"
    else:
        data["failed_installs"] = "0"

    # Serial number & reimage time (case-insensitive)
    for line in lines:
        lower_line = line.lower()
        serial_val = after_keyword(line, keywords["serial"])
        if serial_val is not None:
            data["serial_number"] = serial_val

        time_val = after_keyword(line, keywords["time"])
        if time_val is not None:
            data["reimage_time"] = time_val

    return data


def parse_date_or_none(value: str, label: str):
    """
    Convert a YYYY-MM-DD string to a datetime, or return None if the value is empty/"none"/invalid.
    """
    if not value:
        return None
    if isinstance(value, str) and value.strip().lower() == "none":
        return None
    try:
        return datetime.strptime(value, "%Y-%m-%d")
    except ValueError:
        log("ERROR", f"Invalid {label} '{value}' (expected YYYY-MM-DD). Ignoring this filter.")
        return None


def main():
    parser = argparse.ArgumentParser(description="Process Outlook emails for imaging status.")
    parser.add_argument("folder_name", help="Name of the Outlook folder to search for.")
    parser.add_argument("--test_connection", action="store_true")
    parser.add_argument("--subject_filter", default=None)
    parser.add_argument("--ip_filter", default=None)
    parser.add_argument("--search_mode", default="UNREAD", choices=["UNREAD", "DATE", "RANGE"])
    parser.add_argument("--start_date", default=None)
    parser.add_argument("--end_date", default=None)
    parser.add_argument("--kw_serial", default="Serial Number")
    parser.add_argument("--kw_time", default="Job Total Run Time")
    parser.add_argument("--kw_failed", default="NOTINSTALLED")
    args = parser.parse_args()

    keywords = {
        "serial": args.kw_serial,
        "time": args.kw_time,
        "failed": args.kw_failed,
    }

    # Connect to Outlook
    try:
        outlook = win32com.client.Dispatch("Outlook.Application").GetNamespace("MAPI")
    except Exception as e:
        log("ERROR", f"Could not connect to Outlook. Is it running? Error: {e}")
        sys.exit(1)

    # Find the folder
    try:
        log("INFO", f"Attempting to find folder '{args.folder_name}'...")
        mailbox_root = outlook.GetDefaultFolder(6).Parent  # 6 = Inbox
        folder = find_folder_recursive(mailbox_root, args.folder_name)
        if not folder:
            log("ERROR", f"Could not find folder named '{args.folder_name}'.")
            sys.exit(1)
        log("INFO", f"Successfully accessed folder: '{folder.Name}'.")
    except Exception as e:
        log("ERROR", f"Error accessing Outlook folders: {e}")
        sys.exit(1)

    if args.test_connection:
        print(
            f"SUCCESS: Successfully connected to Outlook and opened folder '{folder.Name}'. "
            f"It contains {folder.Items.Count} total items."
        )
        sys.exit(0)

    log("INFO", "Fetching Outlook items...")
    items = folder.Items

    # Pre-filter in Outlook for speed (Restrict runs inside Outlook instead of Python)
    try:
        criteria = []

        # Only real mail items
        criteria.append("[MessageClass] = 'IPM.Note'")

        # UNREAD mode: let Outlook filter unread up front
        if args.search_mode == "UNREAD":
            criteria.append("[UnRead] = True")

        # Subject substring filter
        if args.subject_filter:
            escaped = args.subject_filter.replace("'", "''")
            criteria.append(f"[Subject] Like '%{escaped}%'")

        # Date / range filtering
        if args.search_mode in ("DATE", "RANGE") and start_dt:
            # Outlook Restrict wants US-style date strings
            start_str = start_dt.strftime("%m/%d/%Y 12:00 AM")
            end_use = start_dt if args.search_mode == "DATE" else (end_dt or start_dt)
            end_str = end_use.strftime("%m/%d/%Y 11:59 PM")
            criteria.append(f"[ReceivedTime] >= '{start_str}' AND [ReceivedTime] <= '{end_str}'")

        restrict_query = " AND ".join(criteria)
        log("INFO", f"Applying Restrict: {restrict_query}")

        all_messages = items.Restrict(restrict_query)

    except Exception as e:
        log("WARN", f"Restrict failed (falling back to manual filtering). {e}")
        all_messages = items

    # Sort AFTER restricting (much faster on a small set)
    all_messages.Sort("[ReceivedTime]", True)

    log("INFO", f"Filtered items count: {all_messages.Count}")

    start_dt = parse_date_or_none(args.start_date, "start_date")
    end_dt = parse_date_or_none(args.end_date, "end_date")

    processed_count = 0

    for message in all_messages:
        try:
            if getattr(message, "Class", None) != 43:
                continue

            if args.search_mode == "UNREAD" and not message.UnRead:
                continue

            if args.subject_filter and args.subject_filter not in (message.Subject or ""):
                continue
            if args.ip_filter and args.ip_filter not in (message.Body or ""):
                continue

            received = getattr(message, "ReceivedTime", None)
            msg_date = None
            if isinstance(received, datetime):
                msg_date = received.date()
            elif received:
                try:
                    msg_date = datetime.strptime(str(received)[:10], "%m/%d/%Y").date()
                except Exception:
                    msg_date = None

            if start_dt and msg_date:
                if args.search_mode == "DATE" and msg_date != start_dt.date():
                    continue
                if args.search_mode == "RANGE":
                    end_date = end_dt.date() if end_dt else start_dt.date()
                    if not (start_dt.date() <= msg_date <= end_date):
                        continue

            subject_match = re.match(r"^([^\s]+)", message.Subject or "")
            if subject_match:
                computer_name = subject_match.group(1).strip()
            else:
                log("WARN", f"Could not parse computer name from subject: '{message.Subject}'.")
                continue

            body_data = parse_email_body(message.Body or "", keywords)

            try:
                received_dt = message.ReceivedTime
                if isinstance(received_dt, datetime):
                    received_str = received_dt.strftime("%Y-%m-%d %H:%M:%S")
                else:
                    received_str = str(received_dt)
            except Exception:
                received_str = "N/A"

            print(
                f"PARSED_EMAIL:{computer_name}_||_{body_data['serial_number']}_||_{body_data['reimage_time']}"
                f"_||_{body_data['failed_installs']}_||_{received_str}"
            )
            processed_count += 1

            if args.search_mode == "UNREAD":
                message.UnRead = False

        except Exception as e:
            log("ERROR", f"Failed to process email '{getattr(message, 'Subject', '')}'. Error: {e}")
            continue

    log("INFO", f"Finished. Processed {processed_count} emails.")


if __name__ == "__main__":
    main()
