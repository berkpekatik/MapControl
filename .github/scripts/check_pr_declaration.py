#!/usr/bin/env python3
"""PR şablonunu ve manuel mühendislik yüzdesini kontrol eder."""

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

DECLARATION = re.compile(
    r"(?m)^[ \t]*[-*][ \t]+\[([ xX])\][ \t]+"
    r"Yaptığım değişiklikleri hiç\s*bir uygulamadan çalmadan "
    r"kendi isteğimle yazdığımı kabul ederim\.?[ \t]*$"
)
PERCENT_LINE = re.compile(
    r"(?im)^[ \t]*manuel mühendislik yüzdesi[ \t]*:[ \t]*(.*)$"
)
PERCENT_VALUE = re.compile(r"(\d{1,3})\s*%\s*")
LABEL_NAME = re.compile(r"^manuel-mühendislik: (\d{1,3})%$")
COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)


def parse_pr(body):
    """Beyan ve yüzde sonucunu döndürür: (işaretli mi, yüzde veya None, hatalar)."""
    text = COMMENT.sub("", body or "").replace("\r\n", "\n").replace("\r", "\n")
    errors = []

    marks = DECLARATION.findall(text)
    if not marks:
        errors.append(
            "Beyan satırı yok. Şablondaki kabul cümlesini silmeyin ve kutuyu işaretleyin."
        )
        checked = False
    else:
        checked = all(mark.lower() == "x" for mark in marks)
        if not checked:
            errors.append(
                "Beyan kutusu işaretli değil. Kabul cümlesini tiklemeden kontrol geçmez."
            )

    lines = PERCENT_LINE.findall(text)
    percent = None
    if len(lines) != 1:
        errors.append(
            "Manuel mühendislik yüzdesi tek satır olmalı. Örnek: Manuel mühendislik yüzdesi: 0%"
        )
    else:
        match = PERCENT_VALUE.fullmatch(lines[0])
        if not match:
            errors.append(
                "Manuel mühendislik yüzdesi okunamadı. Satır yalnızca bir değer içermeli. Örnek: Manuel mühendislik yüzdesi: 25%"
            )
        else:
            percent = int(match.group(1))
            if percent > 100:
                errors.append(
                    f"Manuel mühendislik yüzdesi 0 ile 100 arasında olmalı. Bulunan: {percent}%."
                )
                percent = None

    return checked, percent, errors


def _api(method, path, payload=None):
    token = os.environ["GH_TOKEN"]
    request = urllib.request.Request(
        f"https://api.github.com{path}",
        data=None if payload is None else json.dumps(payload).encode(),
        method=method,
    )
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    request.add_header("User-Agent", "mapcontrol-pr-check")
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = {"message": raw}
        return error.code, parsed


def _label_names(repo, pr_number):
    names = []
    page = 1
    while True:
        status, payload = _api(
            "GET",
            f"/repos/{repo}/issues/{pr_number}/labels?per_page=100&page={page}",
        )
        if status != 200:
            message = payload.get("message", status) if isinstance(payload, dict) else status
            raise RuntimeError(f"Etiketler okunamadı ({message}).")
        if not payload:
            return names
        names.extend(item["name"] for item in payload)
        if len(payload) < 100:
            return names
        page += 1


def assign_percent_label(repo, pr_number, percent):
    """PR'daki eski yüzde etiketini kaldırıp yenisini yazar."""
    label = f"manuel-mühendislik: {percent}%"
    current = _label_names(repo, pr_number)
    status, payload = _api(
        "POST",
        f"/repos/{repo}/labels",
        {
            "name": label,
            "color": "1D76DB",
            "description": "PR açıklamasındaki manuel mühendislik yüzdesi",
        },
    )
    if status not in (200, 201, 422):
        message = payload.get("message", status) if isinstance(payload, dict) else status
        raise RuntimeError(f"Yüzde etiketi oluşturulamadı ({message}).")

    if label not in current:
        status, payload = _api(
            "POST",
            f"/repos/{repo}/issues/{pr_number}/labels",
            {"labels": [label]},
        )
        if status not in (200, 201):
            message = payload.get("message", status) if isinstance(payload, dict) else status
            raise RuntimeError(f"Yüzde etiketi PR'a yazılamadı ({message}).")

    for name in current:
        if LABEL_NAME.match(name) and name != label:
            encoded = urllib.parse.quote(name, safe="")
            status, payload = _api(
                "DELETE",
                f"/repos/{repo}/issues/{pr_number}/labels/{encoded}",
            )
            if status not in (200, 204, 404):
                message = payload.get("message", status) if isinstance(payload, dict) else status
                raise RuntimeError(f"Eski yüzde etiketi silinemedi ({message}).")
    return label


def clear_percent_labels(repo, pr_number):
    for name in _label_names(repo, pr_number):
        if LABEL_NAME.match(name):
            encoded = urllib.parse.quote(name, safe="")
            status, payload = _api(
                "DELETE",
                f"/repos/{repo}/issues/{pr_number}/labels/{encoded}",
            )
            if status not in (200, 204, 404):
                message = payload.get("message", status) if isinstance(payload, dict) else status
                raise RuntimeError(f"Yüzde etiketi silinemedi ({message}).")


def write_summary(percent, errors):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    with open(path, "a", encoding="utf-8") as summary:
        if percent is None:
            summary.write("Manuel mühendislik yüzdesi atanamadı.\n\n")
        else:
            summary.write(f"Manuel mühendislik: **{percent}%**\n\n")
        if errors:
            summary.write("Kontrol başarısız.\n\n")
            for error in errors:
                summary.write(f"- {error}\n")
        else:
            summary.write("Beyan işaretli.\n")


def main():
    body = os.environ.get("PR_BODY") or ""
    _, percent, errors = parse_pr(body)
    repo = os.environ.get("REPO", "")
    pr_number = os.environ.get("PR_NUMBER", "")

    if repo and pr_number and os.environ.get("GH_TOKEN"):
        try:
            if percent is None:
                clear_percent_labels(repo, pr_number)
            else:
                label = assign_percent_label(repo, pr_number, percent)
                print(f"Etiket yazıldı: {label}")
        except RuntimeError as error:
            errors.append(str(error))

    write_summary(percent, errors)
    if errors:
        print("PR kontrolü başarısız:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"Beyan işaretli. Manuel mühendislik: {percent}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
