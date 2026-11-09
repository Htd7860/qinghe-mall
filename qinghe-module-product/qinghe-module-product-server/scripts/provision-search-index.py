"""Explicit offline provisioning; never deletes indexes or rebinds existing aliases.

Uses the same checked-in mapping as ProductSearchIndex. Default is a network-free plan.
Create-index/aliases API: Elasticsearch official 7.17 documentation.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--endpoint', default='http://127.0.0.1:9200')
    parser.add_argument('--alias', required=True)
    parser.add_argument('--physical', required=True)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--verify-existing', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'qinghe-product-[a-z0-9][a-z0-9-]{0,63}', args.alias):
        raise ValueError('Invalid alias name')
    if not re.fullmatch(r'qinghe-product-[a-z0-9][a-z0-9-]{0,111}', args.physical) or args.alias == args.physical:
        raise ValueError('Use a distinct new physical index name')
    uri = urllib.parse.urlsplit(args.endpoint)
    if uri.scheme not in ('http', 'https') or not uri.hostname or uri.username or uri.password or uri.query or uri.fragment or uri.path not in ('', '/'):
        raise ValueError('Endpoint must be a fixed HTTP(S) origin')
    if uri.scheme == 'http' and uri.hostname not in ('localhost', '127.0.0.1', '::1'):
        raise ValueError('Remote endpoint requires HTTPS')
    mapping = json.loads((Path(__file__).resolve().parent.parent / 'src/main/resources/search/product-index-v1.json').read_text(encoding='utf-8'))
    mapping['aliases'] = {args.alias: {'is_write_index': True}}
    if not args.execute:
        print(json.dumps({'mode': 'plan-only', 'method': 'PUT', 'path': '/' + args.physical,
                          'body': mapping}, ensure_ascii=False, indent=2))
        return
    key = os.environ.get('PRODUCT_ES_API_KEY', '')
    if key and (uri.scheme != 'https' or not re.fullmatch(r'[A-Za-z0-9+/=_-]{1,4096}', key)):
        raise ValueError('API key requires HTTPS and a valid format')
    # No ambient HTTP proxies or redirects can forward the API key elsewhere.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(method, path, body=None, allow_missing=False):
        headers = {'Accept': 'application/json'}
        if key:
            headers['Authorization'] = 'ApiKey ' + key
        data = None
        if body is not None:
            headers['Content-Type'] = 'application/json'
            data = json.dumps(body, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
        req = urllib.request.Request(args.endpoint.rstrip('/') + path, data=data, headers=headers, method=method)
        try:
            with opener.open(req, timeout=10) as response:
                raw = response.read(65537)
                if len(raw) > 65536:
                    raise ValueError('ES response exceeds bound')
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            if allow_missing and error.code == 404:
                return None
            raise RuntimeError('ES request unconfirmed, HTTP status ' + str(error.code)) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise RuntimeError('ES request unconfirmed; inspect exact original names before retry') from None

    existing_alias = request('GET', '/_alias/' + args.alias, allow_missing=True)
    existing_physical = request('GET', '/' + args.physical + '/_settings/index.uuid', allow_missing=True)
    if args.verify_existing:
        if not existing_alias or not existing_physical:
            raise ValueError('Original alias/index is absent; verification cannot create it')
    else:
        if existing_alias is not None or existing_physical is not None or request('HEAD', '/' + args.alias, allow_missing=True) is not None:
            raise ValueError('A target name already exists; do not overwrite or rebind it')
        response = request('PUT', '/' + args.physical + '?wait_for_active_shards=1&timeout=5s&master_timeout=5s', mapping)
        if response.get('acknowledged') is not True or response.get('shards_acknowledged') is not True or response.get('index') != args.physical:
            raise RuntimeError('Create result is unknown; use --verify-existing with these exact names, never a new name')
    aliases = request('GET', '/_alias/' + args.alias)
    if set(aliases) != {args.physical} or aliases[args.physical].get('aliases', {}).get(args.alias) != {'is_write_index': True}:
        raise ValueError('Alias binding differs from requested isolated target')
    actual = request('GET', '/' + args.physical + '/_mapping')[args.physical]['mappings']
    if actual != mapping['mappings']:
        raise ValueError('Existing mapping differs; no automatic update is allowed')
    metadata = request('GET', '/' + args.physical + '/_settings/index.uuid')
    uuid = metadata[args.physical]['settings']['index']['uuid']
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', uuid):
        raise ValueError('Index UUID is incomplete')
    print(json.dumps({'status': 'INDEX_PROVISIONED_ONLY', 'alias': args.alias,
                      'physical': args.physical, 'uuid': uuid,
                      'ready': False, 'note': 'No database progress or business record was changed.'}))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, KeyError, json.JSONDecodeError):
        print('Provisioning not confirmed. Inspect original names and metadata; no automatic cleanup or rebind was performed.', file=sys.stderr)
        sys.exit(1)
