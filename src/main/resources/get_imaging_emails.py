import argparse
import re
import sys
import win32com.client
from datetime import datetime, timedelta


# --- LOGGING FUNCTION ---
def log(level, message):
    print(f"LOG:{level}:{message}")


# --- RECURSIVE FOLDER FINDER ---
def find_folder_recursive(base_folder, target_name):
    target_name_lower = target_name.lower()
    if base_folder.Name.lower() == target_name_lower:
        return base_folder
    for subfolder in base_folder.Folders:
        found = find_folder_recursive(subfolder, target_name)
        if found:
            return found
    return None


# --- EMAIL BODY PARSING LOGIC ---
def parse_email_body(body, keywords):
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


# --- MAIN SCRIPT LOGIC ---
def main():
    parser = argparse.ArgumentParser(description="Process Outlook emails for imaging status.")
    parser.add_argument("folder_name", help="Name of the Outlook folder to search for.")
    parser.add_argument("--test_connection", action="store_true", help="Only test the folder connection.")
    parser.add_argument("--subject_filter", default="", help="Filter emails where subject contains this text.")
    parser.add_argument("--ip_filter", default="none", help="IP address prefix to filter by.")
    parser.add_argument("--search_mode", default="UNREAD", choices=["UNREAD", "DATE", "RANGE"], help="Search mode.")
    parser.add_argument("--start_date", default="none", help="Start date for DATE or RANGE mode (YYYY-MM-DD).")
    parser.add_argument("--end_date", default="none", help="End date for RANGE mode (YYYY-MM-DD).")
    parser.add_argument("--kw_comp_name", default="Computer Name:", help="Keyword for Computer Name (ignored).")
    parser.add_argument("--kw_serial", default="Serial Number:", help="Keyword for Serial Number.")
    parser.add_argument("--kw_time", default="Time to reimage:", help="Keyword for Reimage Time.")
    parser.add_argument("--kw_failed", default="items failed to install:", help="Keyword for Failed Installs.")
    args = parser.parse_args()

    keywords = {'serial': args.kw_serial, 'time': args.kw_time, 'failed': args.kw_failed}

    try:
        outlook = win32com.client.Dispatch("Outlook.Application").GetNamespace("MAPI")
    except Exception as e:
        log("ERROR", f"Could not connect to Outlook. Is it running? Error: {e}")
        sys.exit(1)

    try:
        log("INFO", f"Attempting to find folder '{args.folder_name}' in default mailbox...")
        mailbox_root = outlook.GetDefaultFolder(6).Parent
        folder = find_folder_recursive(mailbox_root, args.folder_name)
        if not folder:
            log("ERROR", f"Could not find the folder named '{args.folder_name}' anywhere in your default mailbox.")
            sys.exit(1)
        log("INFO", f"Successfully accessed folder: '{folder.Name}' (Full Path: {folder.FolderPath})")
    except Exception as e:
        log("ERROR", f"An unexpected error occurred while trying to access Outlook folders. Error: {e}")
        sys.exit(1)

    if args.test_connection:
        print(
            f"SUCCESS: Successfully connected to Outlook and found folder '{folder.Name}'. It contains {folder.Items.Count} total items.")
        sys.exit(0)

    filter_str = ""
    if args.search_mode == "UNREAD":
        filter_str = "[Unread] = true"
    elif args.search_mode in ["DATE", "RANGE"] and args.start_date != "none":
        start_dt = datetime.strptime(args.start_date, '%Y-%m-%d')
        if args.search_mode == "RANGE" and args.end_date != "none":
            end_dt = datetime.strptime(args.end_date, '%Y-%m-%d') + timedelta(days=1)
        else:
            end_dt = start_dt + timedelta(days=1)
        start_str = start_dt.strftime('%m/%d/%Y %H:%M %p')
        end_str = end_dt.strftime('%m/%d/%Y %H:%M %p')
        filter_str = f"[ReceivedTime] >= '{start_str}' AND [ReceivedTime] < '{end_str}'"

    if args.subject_filter:
        if filter_str: filter_str += " AND "
        filter_str += f"@SQL=\"urn:schemas:httpmail:subject\" LIKE '%{args.subject_filter}%'"

    log("INFO", f"Using filter: {filter_str}")

    try:
        messages = folder.Items.Restrict(filter_str)
        messages.Sort("[ReceivedTime]", True)
    except Exception as e:
        log("ERROR", f"Failed to apply filter to folder items. Error: {e}")
        sys.exit(1)
    log("INFO", f"Found {messages.Count} item(s) matching filter.")
    processed_count = 0

    for message in list(messages):
        try:
            computer_name = "N/A"
            subject_match = re.search(r'^([^\s]+)', message.Subject)
            if subject_match:
                computer_name = subject_match.group(1).strip()
            else:
                log("WARN", f"Could not parse computer name from subject: '{message.Subject}'. Skipping email.")
                continue
            if args.ip_filter != "none" and args.ip_filter not in message.Body:
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


# --- THIS IS THE FIX ---
# The following lines must be indented to be part of the main script body.
if __name__ == "__main__":
    main()
