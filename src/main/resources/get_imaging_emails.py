import argparse
import os
import re
import sys
from datetime import datetime

import win32com.client

# Self-awareness block for embedded Python
try:
    executable_dir = os.path.dirname(sys.executable)
    sys.path.append(executable_dir)
    sys.path.append(os.path.join(executable_dir, 'Lib', 'site-packages', 'win32'))
    sys.path.append(os.path.join(executable_dir, 'Lib', 'site-packages', 'win32', 'lib'))
except Exception as e:
    print(f"LOG:FATAL:Could not modify Python search path. Error: {e}")


def log(level, message):
    print(f"LOG:{level}:{message}")


def find_folder_recursive(base_folder, target_name):
    target_name_lower = target_name.lower()
    if base_folder.Name.lower() == target_name_lower:
        return base_folder
    for subfolder in base_folder.Folders:
        found = find_folder_recursive(subfolder, target_name)
        if found:
            return found
    return None


def parse_email_body(body, keywords):
    # This function is unchanged and correct
    data = {'serial_number': 'N/A', 'reimage_time': 'N/A', 'failed_installs': '0'}
    lines = body.splitlines()
    failed_apps = []
    summary_found = False
    for line in lines:
        if "the following" in line.lower() and "items failed to install" in line.lower():
            match = re.search(r'(\d+)', line)
            if match:
                data['failed_installs'] = f"{match.group(1)} items (from summary)"
                summary_found = True
                break
    if not summary_found:
        fail_keyword = keywords['failed'].lower()
        for line in lines:
            if line.strip().lower().startswith(fail_keyword):
                parts = line.split('\t')
                if len(parts) > 1:
                    app_name = parts[1].strip()
                    failed_apps.append(app_name)
        if not failed_apps:
            data['failed_installs'] = '0'
        else:
            data['failed_installs'] = ", ".join(failed_apps)
    for line in lines:
        try:
            lower_line = line.lower()
            if keywords['serial'].lower() in lower_line:
                data['serial_number'] = line.split(keywords['serial'], 1)[1].strip()
            elif keywords['time'].lower() in lower_line:
                data['reimage_time'] = line.split(keywords['time'], 1)[1].strip()
        except IndexError:
            log("WARN", f"Could not parse a keyword line: '{line}'")
            continue
    return data


def main():
    parser = argparse.ArgumentParser(description="Process Outlook emails for imaging status.")
    parser.add_argument("folder_name", help="Name of the Outlook folder to search for.")
    parser.add_argument("--test_connection", action="store_true")
    parser.add_argument("--subject_filter", default=None)
    parser.add_argument("--ip_filter", default=None)
    parser.add_argument("--search_mode", default="UNREAD", choices=["UNREAD", "DATE", "RANGE"])
    parser.add_argument("--start_date", default=None)
    parser.add_argument("--end_date", default=None)
    parser.add_argument("--kw_serial", default="Serial Number:")
    parser.add_argument("--kw_time", default="Time to reimage:")
    parser.add_argument("--kw_failed", default="NOTINSTALLED")
    args = parser.parse_args()

    keywords = {'serial': args.kw_serial, 'time': args.kw_time, 'failed': args.kw_failed}

    try:
        outlook = win32com.client.Dispatch("Outlook.Application").GetNamespace("MAPI")
    except Exception as e:
        log("ERROR", f"Could not connect to Outlook. Is it running? Error: {e}")
        sys.exit(1)

    try:
        log("INFO", f"Attempting to find folder '{args.folder_name}'...")
        mailbox_root = outlook.GetDefaultFolder(6).Parent
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
            f"SUCCESS: Successfully connected to Outlook and found folder '{folder.Name}'. It contains {folder.Items.Count} total items.")
        sys.exit(0)

    # --- THIS IS THE NEW RELIABLE FILTERING LOGIC ---
    log("INFO", "Fetching all items from folder to filter in script. This may take a moment...")
    all_messages = folder.Items
    all_messages.Sort("[ReceivedTime]", True)

    log("INFO", f"Found {all_messages.Count} total items. Applying filters now...")

    filtered_messages = []

    start_dt = datetime.strptime(args.start_date, '%Y-%m-%d') if args.start_date else None
    end_dt = datetime.strptime(args.end_date, '%Y-%m-%d') if args.end_date else None

    for message in all_messages:
        # Filter by Unread status
        if args.search_mode == "UNREAD" and message.UnRead is False:
            continue

        # Filter by Date (tz-aware comparison)
        if start_dt:
            received_time_local = message.ReceivedTime.replace(tzinfo=None)
            if args.search_mode == "DATE" and received_time_local.date() != start_dt.date():
                continue
            if args.search_mode == "RANGE":
                if not (start_dt.date() <= received_time_local.date() <= end_dt.date()):
                    continue

        # Filter by Subject
        if args.subject_filter and args.subject_filter.lower() not in message.Subject.lower():
            continue

        # If all checks pass, add it to our list
        filtered_messages.append(message)
    # --- END OF NEW LOGIC ---

    log("INFO", f"Found {len(filtered_messages)} item(s) matching ALL filter criteria.")
    processed_count = 0

    for message in filtered_messages:
        try:
            if args.ip_filter and args.ip_filter not in message.Body:
                continue

            computer_name = "N/A"
            subject_match = re.search(r'^([^\s]+)', message.Subject)
            if subject_match:
                computer_name = subject_match.group(1).strip()
            else:
                log("WARN", f"Could not parse computer name from subject: '{message.Subject}'.")
                continue

            body_data = parse_email_body(message.Body, keywords)
            print(
                f"PARSED_EMAIL:{computer_name}_||_{body_data['serial_number']}_||_{body_data['reimage_time']}_||_{body_data['failed_installs']}")
            processed_count += 1

            if args.search_mode == "UNREAD":
                message.UnRead = False
        except Exception as e:
            log("ERROR", f"Failed to process email with subject '{message.Subject}'. Error: {e}")
            continue

    log("INFO", f"Finished. Processed {processed_count} emails.")


if __name__ == "__main__":
    main()
