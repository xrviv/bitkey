use aws_config::meta::region::RegionProviderChain;
use aws_sdk_s3::{primitives::AggregatedBytes, Client};
use url::Url;

use std::{
    error,
    fmt::{self, Display},
    str::FromStr,
};

#[derive(Debug)]
pub struct S3Uri {
    pub bucket: String,
    pub object: String,
}

impl Display for S3Uri {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "s3://{}/{})", self.bucket.clone(), self.object.clone())
    }
}

impl FromStr for S3Uri {
    type Err = &'static str;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let url = Url::parse(s).map_err(|_| "Invalid S3 URI")?;

        let bucket = url
            .host_str()
            .ok_or("Invalid S3 URI, missing bucket name")?
            .to_string();
        let object = url.path().trim_start_matches('/').to_string();

        if object.is_empty() {
            return Err("Invalid S3 URI, missing object");
        }

        Ok(S3Uri { bucket, object })
    }
}

/// Get an AWS API client.
pub async fn get_client() -> Result<Client, aws_sdk_s3::Error> {
    // Default provider will look for env AWS_REGION
    // See: https://docs.aws.amazon.com/sdk-for-rust/latest/dg/credentials.html
    // also: https://docs.aws.amazon.com/sdk-for-rust/latest/dg/environment-variables.html
    let region_provider = RegionProviderChain::default_provider();
    let config = aws_config::defaults(aws_config::BehaviorVersion::v2023_11_09())
        .region(region_provider)
        .load()
        .await;
    Ok(Client::new(&config))
}

pub async fn read_object(s3_uri: &str) -> Result<AggregatedBytes, Box<dyn error::Error>> {
    let s3_uri = S3Uri::from_str(s3_uri)?;
    let client = get_client().await?;
    let object_stream = client
        .get_object()
        .bucket(&s3_uri.bucket)
        .key(&s3_uri.object)
        .send()
        .await?
        .body;
    Ok(object_stream.collect().await?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use rstest::rstest;

    #[rstest]
    #[case("s3://mybucket/foo.json", "mybucket", "foo.json")]
    #[case(
        "s3://my_horrifically_named_bucket/my/confusing_object/key.json",
        "my_horrifically_named_bucket",
        "my/confusing_object/key.json"
    )]
    #[test]
    fn given_good_input_s3uri_fromstr_works(
        #[case] input: &str,
        #[case] bucket: &str,
        #[case] object: &str,
    ) {
        let s3uri = S3Uri::from_str(input).unwrap();
        assert_eq!(s3uri.bucket, bucket);
        assert_eq!(s3uri.object, object);
    }

    #[rstest]
    #[case("s3://foo.json", "missing object")]
    #[case("s3://", "missing bucket")]
    #[test]
    fn given_bad_input_s3uri_fromstr_fails(#[case] input: &str, #[case] message: &str) {
        let s3uri_err = S3Uri::from_str(input).unwrap_err();
        assert!(
            s3uri_err.contains(message),
            "{}",
            "given {input} did not find '{message}' in '{s3uri_err}"
        );
    }
}
